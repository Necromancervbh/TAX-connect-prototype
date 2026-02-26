package com.example.taxconnect.data.repositories

import com.example.taxconnect.core.utils.FirestoreExtensions
import com.example.taxconnect.data.models.MessageModel
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.data.services.CloudinaryHelper
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    data class PageResult<T>(val items: List<T>, val lastSnapshot: DocumentSnapshot?)

    // --- Chat ID ---

    fun getChatId(uid1: String, uid2: String): String {
        return if (uid1.compareTo(uid2) < 0) "${uid1}_$uid2" else "${uid2}_$uid1"
    }

    // --- Messages ---

    suspend fun sendMessage(message: MessageModel) {
        val chatId = getChatId(message.senderId!!, message.receiverId!!)
        message.chatId = chatId

        firestore.collection("conversations").document(chatId)
            .collection("messages").add(message).await()

        updateConversation(message)
    }

    suspend fun updateMessage(message: MessageModel) {
        val chatId = message.chatId ?: return
        val messageId = message.id ?: return

        val updates = mutableMapOf<String, Any?>()
        message.proposalStatus?.let { updates["proposalStatus"] = it }
        message.paymentRequestStatus?.let { updates["paymentRequestStatus"] = it }
        message.paymentDeclineReason?.let { updates["paymentDeclineReason"] = it }
        message.proposalAdvancePaid?.let { updates["proposalAdvancePaid"] = it }
        message.proposalFinalPaid?.let { updates["proposalFinalPaid"] = it }
        message.proposalPaymentStage?.let { updates["proposalPaymentStage"] = it }

        if (updates.isNotEmpty()) {
            firestore.collection("conversations").document(chatId)
                .collection("messages").document(messageId)
                .update(updates).await()
        }
    }

    private suspend fun updateConversation(message: MessageModel) {
        val chatId = message.chatId ?: return
        val participants = listOf(message.senderId, message.receiverId)

        val updates = mutableMapOf<String, Any?>()
        updates["conversationId"] = chatId
        updates["participantIds"] = participants

        var lastMsg = message.message
        when {
            "DOCUMENT" == message.type -> lastMsg = "📄 ${lastMsg ?: "Document"}"
            !message.imageUrl.isNullOrEmpty() -> lastMsg = "📷 Image"
            "PROPOSAL" == message.type -> lastMsg = "Proposal: ${message.proposalDescription}"
        }

        updates["lastMessage"] = lastMsg ?: ""
        updates["lastMessageTimestamp"] = message.timestamp

        val receiverId = message.receiverId
        if (receiverId != null) {
            updates["unreadCounts.$receiverId"] = FieldValue.increment(1)
        }

        firestore.collection("conversations").document(chatId)
            .set(updates, SetOptions.merge()).await()
    }

    suspend fun markMessagesAsRead(chatId: String, userId: String) {
        firestore.collection("conversations").document(chatId)
            .update("unreadCounts.$userId", 0).await()
    }

    // --- Real-time Flows ---

    fun getMessagesFlow(chatId: String): Flow<List<MessageModel>> = callbackFlow {
        val registration = firestore.collection("conversations").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(50)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    close(Exception(e.message))
                    return@addSnapshotListener
                }
                val messages = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(MessageModel::class.java)?.also { it.id = doc.id }
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { registration.remove() }
    }

    fun getConversationFlow(chatId: String): Flow<ConversationModel> = callbackFlow {
        val registration = firestore.collection("conversations").document(chatId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(Exception(e.message))
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val conversation = snapshot.toObject(ConversationModel::class.java)
                    if (conversation != null) trySend(conversation)
                }
            }
        awaitClose { registration.remove() }
    }

    // --- Pagination ---

    suspend fun getMessagesPage(
        chatId: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?
    ): PageResult<MessageModel> {
        var query = firestore.collection("conversations").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        val snapshot = query.get().await()
        val messages = snapshot.documents.mapNotNull { doc ->
            doc.toObject(MessageModel::class.java)?.also { it.id = doc.id }
        }.reversed()
        val lastDoc = snapshot.documents.lastOrNull()
        return PageResult(messages, lastDoc)
    }

    // --- Payment Status ---

    suspend fun updatePaymentStatus(
        chatId: String,
        messageId: String,
        status: String,
        declineReason: String? = null,
        stage: String? = null
    ) {
        val msg = MessageModel()
        msg.id = messageId
        msg.chatId = chatId

        when (status) {
            "ACCEPTED" -> {
                msg.proposalStatus = "ACCEPTED"
                if (stage != null) msg.proposalPaymentStage = stage
            }
            "REJECTED" -> msg.proposalStatus = "REJECTED"
            "ADVANCE_PAID" -> {
                msg.proposalStatus = "ACCEPTED"
                msg.proposalAdvancePaid = true
                msg.proposalPaymentStage = "FINAL_DUE"
                msg.paymentRequestStatus = "ADVANCE_PAID"
            }
            "FINAL_PAID" -> {
                msg.proposalStatus = "COMPLETED"
                msg.proposalFinalPaid = true
                msg.proposalPaymentStage = "COMPLETED"
                msg.paymentRequestStatus = "FINAL_PAID"
            }
            "PAID" -> msg.paymentRequestStatus = "PAID"
            "DECLINED" -> {
                msg.paymentRequestStatus = "DECLINED"
                msg.paymentDeclineReason = declineReason
            }
            else -> msg.paymentRequestStatus = status
        }

        updateMessage(msg)
    }

    // --- File Upload ---

    suspend fun uploadFile(filePath: String): String = suspendCancellableCoroutine { continuation ->
        CloudinaryHelper.uploadFile(filePath, object : CloudinaryHelper.ImageUploadCallback {
            override fun onSuccess(url: String?) {
                if (continuation.isActive) {
                    if (url != null) continuation.resume(url)
                    else continuation.resumeWithException(Exception("Upload failed"))
                }
            }
            override fun onError(error: String?) {
                if (continuation.isActive) continuation.resumeWithException(Exception(error))
            }
        })
    }
}