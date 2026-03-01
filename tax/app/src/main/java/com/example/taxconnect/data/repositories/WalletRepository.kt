package com.example.taxconnect.data.repositories

import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.data.models.TransactionModel
import com.example.taxconnect.data.models.UserModel
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletRepository @Inject constructor() {

    companion object {
        @Volatile
        private var instance: WalletRepository? = null

        @JvmStatic
        fun getInstance(): WalletRepository {
            return instance ?: synchronized(this) {
                instance ?: WalletRepository().also { instance = it }
            }
        }
    }

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    data class PageResult<T>(val items: List<T>, val lastSnapshot: DocumentSnapshot?)
    data class RevenueBreakdown(val today: Double, val thisWeek: Double, val thisMonth: Double)

    suspend fun getUser(uid: String): UserModel? {
        val snapshot = firestore.collection("users").document(uid).get().await()
        return if (snapshot.exists()) snapshot.toObject(UserModel::class.java) else null
    }

    suspend fun getWalletBalance(uid: String): Double {
        val snapshot = firestore.collection("users").document(uid).get().await()
        if (!snapshot.exists()) throw Exception("User not found")
        val user = snapshot.toObject(UserModel::class.java)
        return user?.walletBalance ?: 0.0
    }

    suspend fun updateWalletBalance(uid: String, amount: Double): Double {
        val userRef = firestore.collection("users").document(uid)
        return firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            if (!snapshot.exists()) throw Exception("User not found")

            val currentBalance = snapshot.getDouble("walletBalance") ?: 0.0
            val newBalance = currentBalance + amount

            if (newBalance < 0) throw Exception("Insufficient balance for this transaction")

            transaction.update(userRef, "walletBalance", newBalance)
            newBalance
        }.await()
    }

    suspend fun createTransaction(tx: TransactionModel) {
        val txId = tx.transactionId ?: UUID.randomUUID().toString()
        tx.transactionId = txId
        if (tx.timestamp == null) tx.timestamp = Timestamp.now()
        firestore.collection("transactions").document(txId).set(tx).await()
    }

    suspend fun getTransactions(uid: String): List<TransactionModel> {
        return getTransactionsPage(uid, 50, null).items
    }

    suspend fun getTransactionsPage(
        uid: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?
    ): PageResult<TransactionModel> {
        var query: Query = firestore.collection("transactions")
            .whereEqualTo("userId", uid)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        val snapshot = query.get().await()
        val transactions = snapshot.documents.mapNotNull { doc ->
            try {
                doc.toObject(TransactionModel::class.java)
            } catch (e: Exception) {
                // Fallback for legacy timestamps
                try {
                    val id = doc.getString("transactionId")
                    val userId = doc.getString("userId")
                    val caId = doc.getString("caId")
                    val caName = doc.getString("caName")
                    val desc = doc.getString("description")
                    val amt = doc.getDouble("amount") ?: 0.0
                    val st = doc.getString("status")
                    val razorpayId = doc.getString("razorpayPaymentId")
                    val tsObj = doc.get("timestamp")
                    val ts = when (tsObj) {
                        is Timestamp -> tsObj
                        is Long -> Timestamp(java.util.Date(tsObj))
                        else -> Timestamp.now()
                    }
                    TransactionModel(id, userId, caId, caName, desc, amt, st, ts).also {
                        it.razorpayPaymentId = razorpayId
                    }
                } catch (_: Exception) { null }
            }
        }
        val lastDoc = snapshot.documents.lastOrNull()
        return PageResult(transactions, lastDoc)
    }

    suspend fun getCaTransactions(caId: String): List<TransactionModel> {
        try {
            val snapshot = firestore.collection("transactions")
                .whereEqualTo("caId", caId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(50)
                .get().await()
            return snapshot.documents.mapNotNull { it.toObject(TransactionModel::class.java) }
        } catch (e: Exception) {
            // Fallback if index not ready
            val snapshot = firestore.collection("transactions")
                .whereEqualTo("caId", caId)
                .limit(50)
                .get().await()
            return snapshot.documents
                .mapNotNull { it.toObject(TransactionModel::class.java) }
                .sortedByDescending { it.timestamp }
        }
    }

    // --- Revenue ---

    suspend fun getRevenueStats(uid: String): Double {
        val snapshot = firestore.collection("transactions")
            .whereEqualTo("caId", uid)
            .whereEqualTo("status", "SUCCESS")
            .get().await()
        return snapshot.documents.sumOf { it.getDouble("amount") ?: 0.0 }
    }

    suspend fun getRevenueBreakdown(uid: String): RevenueBreakdown {
        val now = System.currentTimeMillis()
        val startOfDay = now - (now % (24 * 60 * 60 * 1000))
        val startOfWeek = now - (7 * 24 * 60 * 60 * 1000L)
        val startOfMonth = now - (30 * 24 * 60 * 60 * 1000L)

        val snapshot = firestore.collection("transactions")
            .whereEqualTo("caId", uid)
            .whereEqualTo("status", "SUCCESS")
            .get().await()

        var today = 0.0
        var thisWeek = 0.0
        var thisMonth = 0.0

        for (doc in snapshot.documents) {
            val amount = doc.getDouble("amount") ?: 0.0
            val tsObj = doc.get("timestamp")
            val ts = when (tsObj) {
                is Timestamp -> tsObj.toDate().time
                is Long -> tsObj
                else -> 0L
            }

            if (ts >= startOfDay) today += amount
            if (ts >= startOfWeek) thisWeek += amount
            if (ts >= startOfMonth) thisMonth += amount
        }

        return RevenueBreakdown(today, thisWeek, thisMonth)
    }

    // --- Wallet Payment (Atomic Firestore Transaction) ---

    suspend fun processWalletPayment(
        payerId: String,
        payeeId: String,
        amount: Double,
        description: String,
        chatId: String,
        messageId: String,
        newStatus: String
    ) {
        firestore.runTransaction { transaction ->
            val payerRef = firestore.collection("users").document(payerId)
            val payeeRef = firestore.collection("users").document(payeeId)
            val messageRef = firestore.collection("conversations").document(chatId)
                .collection("messages").document(messageId)
            val conversationRef = firestore.collection("conversations").document(chatId)

            val payerSnapshot = transaction.get(payerRef)
            val payeeSnapshot = transaction.get(payeeRef)

            val payerBalance = payerSnapshot.getDouble("walletBalance") ?: 0.0
            if (payerBalance < amount) throw Exception("Insufficient Balance")

            val payeeBalance = payeeSnapshot.getDouble("walletBalance") ?: 0.0

            transaction.update(payerRef, "walletBalance", payerBalance - amount)
            transaction.update(payeeRef, "walletBalance", payeeBalance + amount)

            val debitId = UUID.randomUUID().toString()
            val debitTx = TransactionModel(
                debitId, payerId, payeeId, "CA", description, -amount, "SUCCESS", Timestamp.now()
            )
            val creditId = UUID.randomUUID().toString()
            val creditTx = TransactionModel(
                creditId, payeeId, payerId, "User", description, amount, "SUCCESS", Timestamp.now()
            )
            transaction.set(firestore.collection("transactions").document(debitId), debitTx)
            transaction.set(firestore.collection("transactions").document(creditId), creditTx)

            val updates = HashMap<String, Any>()
            when (newStatus) {
                "ADVANCE_PAID" -> {
                    updates["proposalStatus"] = "ACCEPTED"
                    updates["proposalAdvancePaid"] = true
                    updates["proposalPaymentStage"] = "FINAL_DUE"
                    updates["paymentRequestStatus"] = "ADVANCE_PAID"
                    transaction.update(conversationRef, "workflowState", ConversationModel.STATE_ADVANCE_PAYMENT)
                }
                "FINAL_PAID" -> {
                    updates["proposalStatus"] = "COMPLETED"
                    updates["proposalFinalPaid"] = true
                    updates["proposalPaymentStage"] = "COMPLETED"
                    updates["paymentRequestStatus"] = "FINAL_PAID"
                    transaction.update(conversationRef, "workflowState", ConversationModel.STATE_COMPLETED)
                    transaction.update(conversationRef, "lastServiceCompletedAt", System.currentTimeMillis())
                }
                "PAID" -> updates["paymentRequestStatus"] = "PAID"
            }
            transaction.update(messageRef, updates)
            null
        }.await()
    }

    suspend fun processExternalPayment(
        payerId: String,
        payeeId: String,
        amount: Double,
        description: String,
        chatId: String,
        messageId: String,
        newStatus: String,
        externalTransactionId: String
    ) {
        firestore.runTransaction { transaction ->
            val payerRef = firestore.collection("users").document(payerId)
            val payeeRef = firestore.collection("users").document(payeeId)
            val messageRef = firestore.collection("conversations").document(chatId)
                .collection("messages").document(messageId)
            val conversationRef = firestore.collection("conversations").document(chatId)

            val payerSnapshot = transaction.get(payerRef)
            val payeeSnapshot = transaction.get(payeeRef)

            val payerName = payerSnapshot.getString("name") ?: "User"
            val payerEmail = payerSnapshot.getString("email")
            val payeeName = payeeSnapshot.getString("name") ?: "CA"

            val txId = UUID.randomUUID().toString()
            val tx = TransactionModel(
                txId, payerId, payeeId, payeeName, description, -amount, "SUCCESS", Timestamp.now()
            )
            tx.type = "SERVICE_PAYMENT"
            tx.userName = payerName
            tx.userEmail = payerEmail
            tx.razorpayPaymentId = externalTransactionId
            transaction.set(firestore.collection("transactions").document(txId), tx)

            val updates = HashMap<String, Any>()
            when (newStatus) {
                "ADVANCE_PAID" -> {
                    updates["proposalStatus"] = "ACCEPTED"
                    updates["proposalAdvancePaid"] = true
                    updates["proposalPaymentStage"] = "FINAL_DUE"
                    updates["paymentRequestStatus"] = "ADVANCE_PAID"
                    transaction.update(conversationRef, "workflowState", ConversationModel.STATE_ADVANCE_PAYMENT)
                }
                "FINAL_PAID" -> {
                    updates["proposalStatus"] = "COMPLETED"
                    updates["proposalFinalPaid"] = true
                    updates["proposalPaymentStage"] = "COMPLETED"
                    updates["paymentRequestStatus"] = "FINAL_PAID"
                    transaction.update(conversationRef, "workflowState", ConversationModel.STATE_COMPLETED)
                    transaction.update(conversationRef, "lastServiceCompletedAt", System.currentTimeMillis())
                }
                "PAID" -> updates["paymentRequestStatus"] = "PAID"
            }
            transaction.update(messageRef, updates)
            null
        }.await()
    }
}