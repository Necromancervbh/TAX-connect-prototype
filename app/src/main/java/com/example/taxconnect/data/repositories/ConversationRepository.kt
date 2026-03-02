package com.example.taxconnect.data.repositories

import com.example.taxconnect.core.utils.FirestoreExtensions
import com.example.taxconnect.data.models.ConversationModel
import com.example.taxconnect.data.models.MessageModel
import com.example.taxconnect.data.models.UserModel
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.awaitClose
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    companion object {
        @Volatile
        private var instance: ConversationRepository? = null

        @JvmStatic
        fun getInstance(): ConversationRepository {
            return instance ?: synchronized(this) {
                instance ?: ConversationRepository().also { instance = it }
            }
        }
    }

    fun getChatId(uid1: String, uid2: String): String {
        return if (uid1.compareTo(uid2) < 0) "${uid1}_$uid2" else "${uid2}_$uid1"
    }

    // --- Conversation CRUD ---

    suspend fun createConversation(conversation: ConversationModel) {
        val id = conversation.conversationId!!
        firestore.collection("conversations").document(id).set(conversation).await()
    }

    fun getConversationsFlow(userId: String): Flow<List<ConversationModel>> = callbackFlow {
        val registration = firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    close(Exception(e.message))
                    return@addSnapshotListener
                }
                val conversations = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(ConversationModel::class.java)
                }?.filter { conv ->
                    conv.workflowState != ConversationModel.STATE_REQUESTED &&
                    conv.workflowState != ConversationModel.STATE_REFUSED
                } ?: emptyList()

                if (conversations.isEmpty()) {
                    trySend(conversations)
                    return@addSnapshotListener
                }

                // Populate user details
                populateUserDetailsAsync(userId, conversations) { enriched ->
                    trySend(enriched)
                }
            }
        awaitClose { registration.remove() }
    }

    suspend fun getConversations(userId: String): List<ConversationModel> {
        val snapshot = firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .get().await()

        val conversations = snapshot.documents
            .mapNotNull { it.toObject(ConversationModel::class.java) }
            .filter {
                it.workflowState != ConversationModel.STATE_REQUESTED &&
                it.workflowState != ConversationModel.STATE_REFUSED
            }

        if (conversations.isEmpty()) return conversations
        return populateUserDetails(userId, conversations)
    }

    suspend fun getRequests(userId: String): List<ConversationModel> {
        val snapshot = firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .get().await()

        val requests = snapshot.documents
            .mapNotNull { it.toObject(ConversationModel::class.java) }
            .filter { it.workflowState == ConversationModel.STATE_REQUESTED }

        if (requests.isEmpty()) return requests
        return populateUserDetails(userId, requests)
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

    fun listenToConversation(chatId: String): Flow<ConversationModel> {
        return getConversationFlow(chatId)
    }

    fun listenToUnreadCount(chatId: String, userId: String): Flow<Int> = callbackFlow {
        val registration = firestore.collection("conversations").document(chatId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    close(Exception(e.message))
                    return@addSnapshotListener
                }
                val count = snapshot?.getLong("unreadCounts.$userId")?.toInt() ?: 0
                trySend(count)
            }
        awaitClose { registration.remove() }
    }

    // --- State Management ---

    suspend fun updateConversationState(chatId: String, state: String) {
        val ref = firestore.collection("conversations").document(chatId)
        FirestoreExtensions.updateFieldWithRetry(ref, "workflowState", state)
    }

    suspend fun updateVideoCallPermission(chatId: String, allowed: Boolean) {
        val ref = firestore.collection("conversations").document(chatId)
        FirestoreExtensions.updateFieldWithRetry(ref, "videoCallAllowed", allowed)
    }

    suspend fun updateCallStatus(chatId: String, status: String) {
        val ref = firestore.collection("conversations").document(chatId)
        FirestoreExtensions.updateFieldWithRetry(ref, "callStatus", status)
    }

     // Overload for legacy callback-based callers (e.g. BroadcastReceivers, Activities)
    fun updateCallStatus(chatId: String, status: String, callback: com.example.taxconnect.data.repositories.DataRepository.DataCallback<Void?>?) {
        firestore.collection("conversations").document(chatId)
            .update("callStatus", status)
            .addOnSuccessListener { callback?.onSuccess(null) }
            .addOnFailureListener { e -> callback?.onError(e.message) }
    }

    // Overload for legacy callback-based callers
    fun listenToConversation(chatId: String, callback: com.example.taxconnect.data.repositories.DataRepository.DataCallback<ConversationModel?>): ListenerRegistration {
        return firestore.collection("conversations").document(chatId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    callback.onError(e.message)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    callback.onSuccess(snapshot.toObject(ConversationModel::class.java))
                } else {
                    callback.onSuccess(null)
                }
            }
    }

    // --- Request Handling ---

    suspend fun sendMessage(message: MessageModel) {
        val chatId = message.chatId ?: return
        firestore.collection("conversations").document(chatId)
            .collection("messages").add(message).await()
        updateConversationForMessage(message)
        triggerBackendNotification(message)
    }

    // Callback overload for sendMessage
    fun sendMessage(message: MessageModel, callback: com.example.taxconnect.data.repositories.DataRepository.DataCallback<Void?>?) {
        val chatId = message.chatId ?: return
        firestore.collection("conversations").document(chatId)
            .collection("messages").add(message)
            .addOnSuccessListener {
                triggerBackendNotification(message)
                
                // We also need to update conversation, but we can't easily await here.
                // Fire and forget or use a scope? For now, just fire the update separately.
                // Ideally we should chain them.
                val updates = mutableMapOf<String, Any?>(
                    "conversationId" to chatId,
                    "participantIds" to listOf(message.senderId, message.receiverId),
                    "lastMessage" to (message.message ?: ""),
                    "lastMessageTimestamp" to message.timestamp
                )
                firestore.collection("conversations").document(chatId)
                    .set(updates, SetOptions.merge())
                    .addOnSuccessListener { callback?.onSuccess(null) }
                    .addOnFailureListener { e -> callback?.onError(e.message) }
            }
            .addOnFailureListener { e -> callback?.onError(e.message) }
    }

    // Callback overload for createConversation
    fun createConversation(conversation: ConversationModel, callback: com.example.taxconnect.data.repositories.DataRepository.DataCallback<Void?>?) {
        val id = conversation.conversationId!!
        firestore.collection("conversations").document(id).set(conversation)
            .addOnSuccessListener { callback?.onSuccess(null) }
            .addOnFailureListener { e -> callback?.onError(e.message) }
    }

    // Callback overload for sendRequest
    fun sendRequest(senderId: String, receiverId: String, initialMessageText: String, callback: com.example.taxconnect.data.repositories.DataRepository.DataCallback<Void?>?) {
        val chatId = getChatId(senderId, receiverId)
        firestore.collection("conversations").document(chatId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val existing = snapshot.toObject(ConversationModel::class.java)
                    if (existing != null && ConversationModel.STATE_REFUSED == existing.workflowState) {
                        val updates = mapOf<String, Any?>(
                            "workflowState" to ConversationModel.STATE_REQUESTED,
                            "lastMessage" to initialMessageText,
                            "lastMessageTimestamp" to System.currentTimeMillis()
                         )
                         firestore.collection("conversations").document(chatId).update(updates)
                             .addOnSuccessListener { callback?.onSuccess(null) }
                             .addOnFailureListener { e -> callback?.onError(e.message) }
                     } else if (ConversationModel.STATE_REQUESTED == existing?.workflowState) {
                         val updates = mapOf<String, Any?>(
                             "lastMessage" to initialMessageText,
                             "lastMessageTimestamp" to System.currentTimeMillis()
                         )
                         firestore.collection("conversations").document(chatId).update(updates)
                             .addOnSuccessListener { callback?.onSuccess(null) }
                             .addOnFailureListener { e -> callback?.onError(e.message) }
                     } else {
                         val msg = MessageModel(
                             senderId, receiverId, chatId, initialMessageText,
                             System.currentTimeMillis(), "TEXT"
                         )
                         sendMessage(msg, callback)
                     }
                } else {
                    val newConv = ConversationModel()
                    newConv.conversationId = chatId
                    newConv.participantIds = listOf(senderId, receiverId)
                    newConv.workflowState = ConversationModel.STATE_REQUESTED
                    newConv.lastMessage = initialMessageText
                    newConv.lastMessageTimestamp = System.currentTimeMillis()
                    firestore.collection("conversations").document(chatId).set(newConv)
                        .addOnSuccessListener { callback?.onSuccess(null) }
                        .addOnFailureListener { e -> callback?.onError(e.message) }
                }
            }
            .addOnFailureListener { e -> callback?.onError(e.message) }
    }

    suspend fun sendRequest(
        senderId: String,
        receiverId: String,
        initialMessageText: String
    ) {
        val chatId = getChatId(senderId, receiverId)
        val snapshot = firestore.collection("conversations").document(chatId).get().await()

        if (snapshot.exists()) {
            val existing = snapshot.toObject(ConversationModel::class.java)
            if (existing != null && ConversationModel.STATE_REFUSED == existing.workflowState) {
                val updates = mapOf<String, Any?>(
                    "workflowState" to ConversationModel.STATE_REQUESTED,
                    "lastMessage" to initialMessageText,
                    "lastMessageTimestamp" to System.currentTimeMillis()
                )
                firestore.collection("conversations").document(chatId).update(updates).await()
            } else if (ConversationModel.STATE_REQUESTED == existing?.workflowState) {
                val updates = mapOf<String, Any?>(
                    "lastMessage" to initialMessageText,
                    "lastMessageTimestamp" to System.currentTimeMillis()
                )
                firestore.collection("conversations").document(chatId).update(updates).await()
            } else {
                // Conversation exists and is active, send as message
                val msg = MessageModel(
                    senderId, receiverId, chatId, initialMessageText,
                    System.currentTimeMillis(), "TEXT"
                )
                firestore.collection("conversations").document(chatId)
                    .collection("messages").add(msg).await()
                updateConversationForMessage(msg)
            }
        } else {
            val newConv = ConversationModel()
            newConv.conversationId = chatId
            newConv.participantIds = listOf(senderId, receiverId)
            newConv.workflowState = ConversationModel.STATE_REQUESTED
            newConv.lastMessage = initialMessageText
            newConv.lastMessageTimestamp = System.currentTimeMillis()
            firestore.collection("conversations").document(chatId).set(newConv).await()
        }
    }

    suspend fun acceptRequest(chatId: String) {
        updateConversationState(chatId, ConversationModel.STATE_ACCEPTED)
    }

    // --- Service Cycle ---

    suspend fun startNewServiceCycle(chatId: String) {
        val cycleId = UUID.randomUUID().toString()
        val updates = mapOf(
            "workflowState" to ConversationModel.STATE_DISCUSSION,
            "currentServiceCycleId" to cycleId,
            "lastServiceStartedAt" to System.currentTimeMillis()
        )
        firestore.collection("conversations").document(chatId).update(updates).await()
    }

    suspend fun completeServiceCycle(chatId: String, cycleId: String? = null) {
        val updates = mutableMapOf<String, Any?>(
            "workflowState" to ConversationModel.STATE_DISCUSSION,
            "lastServiceCompletedAt" to System.currentTimeMillis()
        )
        val resolvedCycleId = cycleId ?: run {
            val snapshot = firestore.collection("conversations").document(chatId).get().await()
            snapshot.getString("currentServiceCycleId")
        }
        if (resolvedCycleId != null) {
            updates["serviceCycleHistory.$resolvedCycleId.completedAt"] = System.currentTimeMillis()
            updates["serviceCycleHistory.$resolvedCycleId.status"] = "COMPLETED"
        }
        firestore.collection("conversations").document(chatId).update(updates).await()
    }

    suspend fun updateProposalStatus(chatId: String, messageId: String, status: String) {
        val ref = firestore.collection("conversations").document(chatId)
            .collection("messages").document(messageId)
        FirestoreExtensions.updateFieldWithRetry(ref, "proposalStatus", status)
    }

    suspend fun appendServiceCycleStatus(chatId: String, statusUpdate: String) {
        val ref = firestore.collection("conversations").document(chatId)
        ref.update("serviceCycleStatus", FieldValue.arrayUnion(
            mapOf("status" to statusUpdate, "timestamp" to System.currentTimeMillis())
        )).await()
    }

    suspend fun getServiceHistory(chatId: String): Map<String, Any>? {
        val snapshot = firestore.collection("conversations").document(chatId).get().await()
        @Suppress("UNCHECKED_CAST")
        return snapshot.get("serviceCycleHistory") as? Map<String, Any>
    }

    // --- User Detail Population ---

    private suspend fun populateUserDetails(
        userId: String,
        conversations: List<ConversationModel>
    ): List<ConversationModel> {
        val tasks = conversations.mapNotNull { conv ->
            conv.participantIds?.firstOrNull { it != userId }?.let { otherId ->
                firestore.collection("users").document(otherId).get()
            }
        }
        if (tasks.isEmpty()) return conversations

        try {
            val results = Tasks.whenAllSuccess<DocumentSnapshot>(tasks).await()
            for (userDoc in results) {
                if (userDoc.exists()) {
                    val user = userDoc.toObject(UserModel::class.java)
                    if (user != null) {
                        conversations.filter { it.participantIds?.contains(user.uid) == true }
                            .forEach { conv ->
                                conv.otherUserName = user.name
                                conv.otherUserEmail = user.email
                                conv.otherUserProfileImage = user.profileImageUrl
                            }
                    }
                }
            }
        } catch (_: Exception) { /* Return partially enriched data */ }
        return conversations
    }

    private fun populateUserDetailsAsync(
        userId: String,
        conversations: List<ConversationModel>,
        callback: (List<ConversationModel>) -> Unit
    ) {
        val tasks = conversations.mapNotNull { conv ->
            conv.participantIds?.firstOrNull { it != userId }?.let { otherId ->
                firestore.collection("users").document(otherId).get()
            }
        }
        if (tasks.isEmpty()) {
            callback(conversations)
            return
        }

        Tasks.whenAllSuccess<DocumentSnapshot>(tasks)
            .addOnSuccessListener { results ->
                for (userDoc in results) {
                    if (userDoc.exists()) {
                        val user = userDoc.toObject(UserModel::class.java)
                        if (user != null) {
                            conversations.filter { it.participantIds?.contains(user.uid) == true }
                                .forEach { conv ->
                                    conv.otherUserName = user.name
                                    conv.otherUserEmail = user.email
                                    conv.otherUserProfileImage = user.profileImageUrl
                                }
                        }
                    }
                }
                callback(conversations)
            }
            .addOnFailureListener { callback(conversations) }
    }



    fun updateConversationForMessage(message: MessageModel) {
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
            "PAYMENT_REQUEST" == message.type -> lastMsg = "💰 Payment Request: ${message.paymentRequestAmount?.let { "₹$it" } ?: lastMsg}"
        }
        updates["lastMessage"] = lastMsg ?: ""
        updates["lastMessageTimestamp"] = message.timestamp
        val receiverId = message.receiverId
        if (receiverId != null) {
            updates["unreadCounts.$receiverId"] = FieldValue.increment(1)
        }
        firestore.collection("conversations").document(chatId)
            .set(updates, SetOptions.merge())
    }

    suspend fun markMessagesAsRead(chatId: String, userId: String) {
        firestore.collection("conversations").document(chatId)
            .update("unreadCounts.$userId", 0).await()
    }

    // --- Payment Status Logic (from ChatRepository) ---
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

    // --- File Upload ---
    suspend fun uploadFile(filePath: String): String = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        com.example.taxconnect.data.services.CloudinaryHelper.uploadFile(filePath, object : com.example.taxconnect.data.services.CloudinaryHelper.ImageUploadCallback {
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

    fun getUnreadCountFlow(userId: String): Flow<Int> = callbackFlow {
        val registration = firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    return@addSnapshotListener
                }
                var total = 0
                snapshots?.documents?.forEach { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val counts = doc.get("unreadCounts") as? Map<String, Long>
                    total += counts?.get(userId)?.toInt() ?: 0
                }
                trySend(total)
            }
        awaitClose { registration.remove() }
    }

    private fun triggerBackendNotification(message: MessageModel) {
        kotlinx.coroutines.GlobalScope.launch {
            try {
                val request = com.example.taxconnect.data.remote.ChatNotificationRequest(
                    recipientId = message.receiverId ?: "",
                    senderId = message.senderId ?: "",
                    senderName = null, // Backend fetches from Firestore
                    senderImage = null, // Backend fetches from Firestore
                    chatId = message.chatId ?: "",
                    messageContent = message.message,
                    messageType = message.type ?: "TEXT"
                )
                
                val response = com.example.taxconnect.data.remote.ApiClient.notificationService.sendChatNotification(request)
                if (!response.isSuccessful) {
                    android.util.Log.e("PushNotification", "Failed to send push: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                android.util.Log.e("PushNotification", "Exception sending push: ${e.message}")
            }
        }
    }

}
