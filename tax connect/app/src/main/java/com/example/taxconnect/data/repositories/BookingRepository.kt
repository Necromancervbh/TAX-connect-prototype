package com.example.taxconnect.data.repositories

import com.example.taxconnect.core.utils.FirestoreExtensions
import com.example.taxconnect.data.models.BookingModel
import com.example.taxconnect.data.models.UserModel
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookingRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    data class PageResult<T>(val items: List<T>, val lastSnapshot: DocumentSnapshot?)

    suspend fun saveBooking(booking: BookingModel) {
        val id = booking.id ?: firestore.collection("bookings").document().id
        booking.id = id
        firestore.collection("bookings").document(id).set(booking).await()
    }

    suspend fun getBookingsForUser(userId: String): List<BookingModel> {
        return getBookingsForUserPage(userId, 50, null).items
    }

    suspend fun getBookingsForCA(caId: String): List<BookingModel> {
        return getBookingsForCaPage(caId, 50, null).items
    }

    fun getBookingsForCaFlow(caId: String): Flow<List<BookingModel>> = callbackFlow {
        val listener = firestore.collection("bookings")
            .whereEqualTo("caId", caId)
            .orderBy("appointmentTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { it.toObject(BookingModel::class.java) }
                launch {
                    try { trySend(populateBookingUserDetails(list)) } catch (_: Exception) { trySend(list) }
                }
            }
        awaitClose { listener.remove() }
    }

    fun getBookingsForUserFlow(userId: String): Flow<List<BookingModel>> = callbackFlow {
        val listener = firestore.collection("bookings")
            .whereEqualTo("userId", userId)
            .orderBy("appointmentTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val list = snapshot.documents.mapNotNull { it.toObject(BookingModel::class.java) }
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getBookingsForUserPage(
        userId: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?
    ): PageResult<BookingModel> {
        try {
            var query: Query = firestore.collection("bookings")
                .whereEqualTo("userId", userId)
                .orderBy("appointmentTimestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }
            val snapshot = query.get().await()
            val list = snapshot.documents.mapNotNull { it.toObject(BookingModel::class.java) }
            val lastDoc = snapshot.documents.lastOrNull()
            return PageResult(list, lastDoc)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("index") || msg.contains("FAILED_PRECONDITION")) {
                // Fallback if composite index not ready
                val fallback = firestore.collection("bookings")
                    .whereEqualTo("userId", userId)
                    .limit(limit.toLong())
                    .get().await()
                val list = fallback.documents
                    .mapNotNull { it.toObject(BookingModel::class.java) }
                    .sortedByDescending { it.appointmentTimestamp }
                return PageResult(list, null)
            }
            throw e
        }
    }

    suspend fun getBookingsForCaPage(
        caId: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?
    ): PageResult<BookingModel> {
        try {
            var query: Query = firestore.collection("bookings")
                .whereEqualTo("caId", caId)
                .orderBy("appointmentTimestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }
            val snapshot = query.get().await()
            val list = snapshot.documents.mapNotNull { it.toObject(BookingModel::class.java) }
            val enriched = populateBookingUserDetails(list)
            val lastDoc = snapshot.documents.lastOrNull()
            return PageResult(enriched, lastDoc)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("index") || msg.contains("FAILED_PRECONDITION")) {
                val fallback = firestore.collection("bookings")
                    .whereEqualTo("caId", caId)
                    .limit(limit.toLong())
                    .get().await()
                val list = fallback.documents
                    .mapNotNull { it.toObject(BookingModel::class.java) }
                    .sortedByDescending { it.appointmentTimestamp }
                val enriched = populateBookingUserDetails(list)
                return PageResult(enriched, null)
            }
            throw e
        }
    }

    private suspend fun populateBookingUserDetails(bookings: List<BookingModel>): List<BookingModel> {
        // Collect unique user IDs (clients) and CA IDs to fetch in one batch
        val userTasks = bookings.mapNotNull { booking ->
            booking.userId?.let { firestore.collection("users").document(it).get() }
        }
        val caTasks = bookings.mapNotNull { booking ->
            booking.caId?.let { firestore.collection("users").document(it).get() }
        }
        val allTasks = userTasks + caTasks
        if (allTasks.isEmpty()) return bookings

        try {
            val results = Tasks.whenAllSuccess<DocumentSnapshot>(allTasks).await()
            for (userDoc in results) {
                if (!userDoc.exists()) continue
                val user = userDoc.toObject(UserModel::class.java) ?: continue
                val uid = user.uid ?: userDoc.id
                // Enrich client name (for CA-side cards)
                bookings.filter { it.userId == uid && it.userName.isNullOrBlank() }
                    .forEach { it.userName = user.name }
                // Enrich CA name (for client-side cards)
                bookings.filter { it.caId == uid && it.caName.isNullOrBlank() }
                    .forEach { it.caName = user.name }
            }
        } catch (_: Exception) {
            // Return bookings without enrichment on failure
        }
        return bookings
    }

    suspend fun updateBookingStatus(bookingId: String, status: String) {
        val ref = firestore.collection("bookings").document(bookingId)
        FirestoreExtensions.updateFieldWithRetry(ref, "status", status)
    }

    suspend fun incrementClientCount(caId: String, userId: String) {
        val clientRef = firestore.collection("users").document(caId)
            .collection("clients").document(userId)
        val snapshot = clientRef.get().await()
        if (!snapshot.exists()) {
            val batch = firestore.batch()
            batch.set(clientRef, mapOf("timestamp" to System.currentTimeMillis()))
            val caRef = firestore.collection("users").document(caId)
            batch.update(caRef, "clientCount", FieldValue.increment(1))
            batch.commit().await()
        }
    }

    suspend fun isReturningClient(caId: String, userId: String): Boolean {
        val snapshot = firestore.collection("users").document(caId)
            .collection("clients").document(userId).get().await()
        return snapshot.exists()
    }

    suspend fun acceptBookingWithChat(booking: BookingModel, caName: String? = null): String? {
        val bookingId = booking.id ?: return null
        val caId = booking.caId ?: return null
        val userId = booking.userId ?: return null
        val serviceName = booking.serviceName ?: "your service"
        val dateStr = booking.appointmentDate ?: ""
        val timeStr = booking.appointmentTime ?: ""
        val now = System.currentTimeMillis()

        // ── Step 1: Find or create conversation ───────────────────────────────
        // (must happen before the batch so we have the convId)
        val convRepo = ConversationRepository.getInstance()
        var convId = convRepo.findExistingChatId(caId, userId)

        if (convId == null) {
            convId = convRepo.getChatId(caId, userId)
            val nc = mapOf(
                "conversationId" to convId,
                "participantIds" to listOf(caId, userId),
                "lastMessage" to "✅ Booking confirmed",
                "lastMessageTimestamp" to now,
                "workflowState" to "DISCUSSION",
                "bookingId" to bookingId,
                "createdAt" to now
            )
            // Use deterministic ID instead of random document ID
            firestore.collection("conversations").document(convId).set(nc).await()
        }

        // ── Step 2: Atomic batch — booking status + chatId + conversation bookingId ──
        val batch = firestore.batch()
        val bookingRef = firestore.collection("bookings").document(bookingId)
        batch.update(bookingRef, mapOf(
            "status" to "ACCEPTED",
            "chatId" to convId,
            "updatedAt" to now
        ))
        val convRef = firestore.collection("conversations").document(convId)
        batch.update(convRef, "bookingId", bookingId)
        batch.commit().await()

        // ── Step 3: Increment client count (non-critical, fire & forget) ───────
        try { incrementClientCount(caId, userId) } catch (_: Exception) {}

        // ── Step 4: System message in chat ────────────────────────────────────
        try {
            val msg = mapOf(
                "senderId" to "SYSTEM",
                "receiverId" to null,
                "chatId" to convId,
                "message" to "✅ Booking confirmed for $serviceName on $dateStr at $timeStr. You can now message each other.",
                "timestamp" to now,
                "type" to "SYSTEM",
                "bookingId" to bookingId
            )
            firestore.collection("conversations").document(convId)
                .collection("messages").add(msg).await()
            firestore.collection("conversations").document(convId).update(mapOf(
                "lastMessage" to "✅ Booking for $serviceName confirmed",
                "lastMessageTimestamp" to now
            )).await()
        } catch (e: Exception) {
            android.util.Log.w("BookingRepo", "System message failed (non-fatal): ${e.message}")
        }

        // ── Step 5: FCM push to CLIENT — deep links straight to ChatActivity ──
        try {
            val req = com.example.taxconnect.data.remote.BookingNotificationRequest(
                recipientId  = userId,
                status       = "ACCEPTED",
                bookingId    = bookingId,
                chatId       = convId,        // ← client notification will open chat
                otherUserId  = caId,
                otherUserName = caName ?: "",
                serviceName  = serviceName,
                date         = dateStr,
                time         = timeStr
            )
            com.example.taxconnect.data.remote.ApiClient.notificationService
                .sendBookingNotification(req)
        } catch (e: Exception) {
            android.util.Log.w("BookingRepo", "FCM push to client failed (non-fatal): ${e.message}")
        }

        return convId
    }

    suspend fun rejectBooking(bookingId: String, reason: String?) {
        val ref = firestore.collection("bookings").document(bookingId)
        val updates = mutableMapOf<String, Any>("status" to "REJECTED", "updatedAt" to System.currentTimeMillis())
        if (!reason.isNullOrBlank()) updates["rejectionReason"] = reason
        ref.update(updates).await()
    }

    suspend fun autoExpirePendingBookingsForCA(caUid: String) {
        val cutoff = System.currentTimeMillis() - 86400000L
        try {
            val snap = firestore.collection("bookings")
                .whereEqualTo("caId", caUid)
                .whereEqualTo("status", "PENDING")
                .whereLessThan("createdAt", cutoff)
                .get().await()
            if (!snap.isEmpty) {
                val batch = firestore.batch()
                snap.documents.forEach { batch.update(it.reference, "status", "EXPIRED") }
                batch.commit().await()
            }
        } catch (_: Exception) { /* ignore */ }
    }

    suspend fun getCompletedCAsForUser(userId: String): List<String> {
        return try {
            firestore.collection("bookings")
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", "COMPLETED")
                .get().await()
                .documents.mapNotNull { it.getString("caId") }.distinct()
        } catch (_: Exception) { emptyList() }
    }

}
