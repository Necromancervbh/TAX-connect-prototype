package com.example.taxconnect.data.repositories

import com.example.taxconnect.data.models.*
import com.example.taxconnect.data.repositories.AnalyticsRepository
import com.example.taxconnect.core.network.NetworkUtils
import com.example.taxconnect.data.services.FirestoreSyncWorker
import android.content.Context
import androidx.work.*
import com.google.gson.Gson
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

/**
 * Repository class to handle backend logic.
 * Integrates Firebase (Auth/Firestore), Cloudinary, and Agora.
 */
class DataRepository private constructor() {

    // Firebase
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val cache: MutableMap<String, CacheEntry> = HashMap()
    private var appContext: Context? = null

    fun init(context: Context) {
        this.appContext = context.applicationContext
    }

    private fun enqueueSync(type: String, data: Map<String, Any?>) {
        val context = appContext ?: return
        val inputData = Data.Builder()
            .putString("sync_type", type)
        
        data.forEach { (key, value) ->
            when (value) {
                is String -> inputData.putString(key, value)
                is Boolean -> inputData.putBoolean(key, value)
                is Int -> inputData.putInt(key, value)
                is Long -> inputData.putLong(key, value)
            }
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<FirestoreSyncWorker>()
            .setConstraints(constraints)
            .setInputData(inputData.build())
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    companion object {
        @Volatile
        private var instance: DataRepository? = null

        @JvmStatic
        fun getInstance(): DataRepository {
            return instance ?: synchronized(this) {
                instance ?: DataRepository().also { instance = it }
            }
        }
    }

    private fun updateFieldWithRetry(
        ref: DocumentReference,
        field: String,
        value: Any?,
        retries: Int,
        callback: DataCallback<Void?>?
    ) {
        ref.update(field, value)
            .addOnSuccessListener {
                callback?.onSuccess(null)
            }
            .addOnFailureListener { e ->
                if (retries > 0) {
                    updateFieldWithRetry(ref, field, value, retries - 1, callback)
                } else {
                    callback?.onError(e.message)
                }
            }
    }

    private data class CacheEntry(val data: Any?, val ts: Long)

    private fun isCacheValid(key: String, ttlMs: Long): Boolean {
        val ce = cache[key]
        return ce != null && (System.currentTimeMillis() - ce.ts) < ttlMs
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> respondFromCache(key: String, callback: DataCallback<T>) {
        val ce = cache[key]
        if (ce != null) {
            val data = ce.data as T
            callback.onSuccess(data)
        } else {
            callback.onError("Cache miss")
        }
    }

    interface DataCallback<T> {
        fun onSuccess(data: T?)
        fun onError(error: String?)
    }

    class PageResult<T>(val items: List<T>, val lastSnapshot: DocumentSnapshot?)

    data class RevenueBreakdown(val today: Double, val week: Double, val month: Double)

    // --- Feedback ---

    fun saveFeedback(feedback: FeedbackModel, callback: DataCallback<Void?>) {
        if (appContext != null && !NetworkUtils.isNetworkAvailable(appContext!!)) {
            enqueueSync("SAVE_FEEDBACK", mapOf("feedback_json" to Gson().toJson(feedback)))
            callback.onSuccess(null)
            return
        }
        val id = firestore.collection("feedback").document().id
        firestore.collection("feedback").document(id).set(feedback)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Services ---

    fun saveService(service: ServiceModel, callback: DataCallback<Void?>) {
        if (appContext != null && !NetworkUtils.isNetworkAvailable(appContext!!)) {
            enqueueSync("SAVE_SERVICE", mapOf("service_json" to Gson().toJson(service)))
            callback.onSuccess(null)
            return
        }
        firestore.collection("services").document(service.id!!).set(service)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun updateCallStatus(chatId: String, status: String, callback: DataCallback<Void?>?) {
        val ref = firestore.collection("conversations").document(chatId)
        updateFieldWithRetry(ref, "callStatus", status, 2, callback)
    }

    fun getServices(caId: String, callback: DataCallback<List<ServiceModel>>) {
        firestore.collection("services").whereEqualTo("caId", caId).get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val list: MutableList<ServiceModel> = ArrayList()
                for (doc in queryDocumentSnapshots) {
                    val service = doc.toObject(ServiceModel::class.java)
                    list.add(service)
                }
                callback.onSuccess(list)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun deleteService(serviceId: String, callback: DataCallback<Void?>) {
        if (appContext != null && !NetworkUtils.isNetworkAvailable(appContext!!)) {
            enqueueSync("DELETE_SERVICE", mapOf("service_id" to serviceId))
            callback.onSuccess(null)
            return
        }
        firestore.collection("services").document(serviceId).delete()
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Documents ---

    fun saveDocument(userId: String, doc: DocumentModel, callback: DataCallback<Boolean>) {
        firestore.collection("users").document(userId).collection("documents").document(doc.id!!).set(doc)
            .addOnSuccessListener { callback.onSuccess(true) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun getDocuments(userId: String, callback: DataCallback<List<DocumentModel>>) {
        firestore.collection("users").document(userId).collection("documents")
            .orderBy("uploadedAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val list: MutableList<DocumentModel> = ArrayList()
                for (doc in queryDocumentSnapshots) {
                    val document = doc.toObject(DocumentModel::class.java)
                    list.add(document)
                }
                callback.onSuccess(list)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Ratings ---

    fun addRating(rating: RatingModel, callback: DataCallback<Void?>) {
        val caId = rating.caId
        val userId = rating.userId

        firestore.runTransaction { transaction ->
            // 1. Reference to CA User document
            val caRef = firestore.collection("users").document(caId!!)
            val caSnapshot = transaction.get(caRef)

            // 2. Reference to User Rating document
            val ratingRef = caRef.collection("ratings").document(userId!!)
            val ratingSnapshot = transaction.get(ratingRef)

            // 3. Logic to update rating
            var currentRating = 0.0
            var currentCount: Long = 0

            if (caSnapshot.exists()) {
                val r = caSnapshot.getDouble("rating")
                val c = caSnapshot.getLong("ratingCount")
                if (r != null) currentRating = r
                if (c != null) currentCount = c
            }

            val newRatingVal = rating.rating
            val newAverage: Double
            val newCount: Long

            if (ratingSnapshot.exists()) {
                // Update existing rating
                var oldRatingVal = 0.0
                val oldR = ratingSnapshot.getDouble("rating")
                if (oldR != null) oldRatingVal = oldR

                // Remove old contribution and add new
                val totalScore = (currentRating * currentCount) - oldRatingVal + newRatingVal
                newCount = currentCount // Count remains same
                newAverage = totalScore / newCount
            } else {
                // New rating
                val totalScore = (currentRating * currentCount) + newRatingVal
                newCount = currentCount + 1
                newAverage = totalScore / newCount
            }

            // 4. Write updates
            transaction.set(ratingRef, rating)
            transaction.update(caRef, "rating", newAverage, "ratingCount", newCount)
            null
        }.addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun getRatings(caId: String, callback: DataCallback<List<RatingModel>>) {
        firestore.collection("users").document(caId).collection("ratings")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val list: MutableList<RatingModel> = ArrayList()
                for (doc in queryDocumentSnapshots) {
                    val rating = doc.toObject(RatingModel::class.java)
                    list.add(rating)
                }
                callback.onSuccess(list)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Users ---

    fun saveUser(user: UserModel, callback: DataCallback<Void?>) {
        if (appContext != null && !NetworkUtils.isNetworkAvailable(appContext!!)) {
            enqueueSync("SAVE_USER", mapOf("user_json" to Gson().toJson(user)))
            callback.onSuccess(null)
            return
        }
        firestore.collection("users").document(user.uid!!).set(user)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun checkRatingEligibility(userId: String, caId: String, callback: DataCallback<Boolean>) {
        // Check if there is any conversation where state is NOT REQUESTED or REFUSED
        firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                var isEligible = false
                for (doc in queryDocumentSnapshots.documents) {
                    val conv = doc.toObject(ConversationModel::class.java)
                    if (conv != null && conv.participantIds?.contains(caId) == true) {
                        val state = conv.workflowState
                        if (ConversationModel.STATE_REQUESTED != state &&
                            ConversationModel.STATE_REFUSED != state
                        ) {
                            isEligible = true;
                            break;
                        }
                    }
                }
                callback.onSuccess(isEligible)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Authentication ---

    fun loginUser(email: String, password: String, callback: DataCallback<UserModel>) {
        try {
            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = task.result.user!!.uid
                        fetchUser(uid, callback)
                    } else {
                        callback.onError(task.exception?.message)
                    }
                }
        } catch (e: Exception) {
            callback.onError("Firebase Init Error: ${e.message}")
        }
    }

    fun registerUser(email: String, password: String, userModel: UserModel, callback: DataCallback<Void?>) {
        try {
            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = task.result.user!!.uid
                        userModel.uid = uid

                        try {
                            firestore.collection("users").document(uid).set(userModel)
                                .addOnSuccessListener { callback.onSuccess(null) }
                                .addOnFailureListener { e -> callback.onError("Firestore Error: " + e.message) }
                        } catch (e: Exception) {
                            callback.onError("Firestore Init Error: ${e.message}")
                        }
                    } else {
                        callback.onError("Auth Error: " + task.exception?.message)
                    }
                }
        } catch (e: Exception) {
            callback.onError("Firebase Init Error: ${e.message}")
        }
    }

    fun fetchUser(uid: String, callback: DataCallback<UserModel>) {
        val cacheKey = "user_$uid"
        if (isCacheValid(cacheKey, 30_000L)) { // 30s cache for user profile
            respondFromCache(cacheKey, callback)
            return
        }

        try {
            firestore.collection("users").document(uid).get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val user = documentSnapshot.toObject(UserModel::class.java)
                        if (user != null) {
                            if (user.uid == null) user.uid = documentSnapshot.id
                            cache[cacheKey] = CacheEntry(user, System.currentTimeMillis())
                            callback.onSuccess(user)
                        } else {
                            callback.onError("User not found in database")
                        }
                    } else {
                        callback.onError("User not found in database")
                    }
                }
                .addOnFailureListener { e -> callback.onError(e.message) }
        } catch (e: Exception) {
            callback.onError("Firestore Init Error: ${e.message}")
        }
    }

    fun clearUserCache(uid: String) {
        cache.remove("user_$uid")
    }

    fun updateUserStatus(uid: String, isOnline: Boolean, callback: DataCallback<Void?>) {
        clearUserCache(uid)
        if (appContext != null && !NetworkUtils.isNetworkAvailable(appContext!!)) {
            enqueueSync("UPDATE_USER_STATUS", mapOf("uid" to uid, "is_online" to isOnline))
            callback.onSuccess(null)
            return
        }
        firestore.collection("users").document(uid)
            .update("isOnline", isOnline)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun updateUser(userModel: UserModel, callback: DataCallback<Void?>) {
        clearUserCache(userModel.uid!!)
        firestore.collection("users").document(userModel.uid!!).set(userModel)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError("Firestore Error: " + e.message) }
    }

    fun updateUserProfile(uid: String, updates: Map<String, Any>, callback: DataCallback<Void?>) {
        clearUserCache(uid)
        firestore.collection("users").document(uid).update(updates)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun updateFcmToken(token: String) {
        if (firebaseAuth.currentUser != null) {
            val uid = firebaseAuth.currentUser!!.uid
            firestore.collection("users").document(uid)
                .update("fcmToken", token)
                .addOnFailureListener {
                    // Log error
                }
        }
    }

    fun getFavoriteCaIds(userId: String, callback: DataCallback<List<String>>) {
        firestore.collection("users").document(userId).collection("favorites")
            .get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val list: MutableList<String> = ArrayList()
                for (doc in queryDocumentSnapshots.documents) {
                    list.add(doc.id)
                }
                callback.onSuccess(list)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun toggleFavorite(userId: String, caId: String, callback: DataCallback<Boolean>) {
        val ref = firestore.collection("users").document(userId).collection("favorites").document(caId)
        ref.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                ref.delete().addOnSuccessListener { callback.onSuccess(false) }
                    .addOnFailureListener { e -> callback.onError(e.message) }
            } else {
                val data: MutableMap<String, Any> = HashMap()
                data["timestamp"] = System.currentTimeMillis()
                data["caId"] = caId
                ref.set(data).addOnSuccessListener { callback.onSuccess(true) }
                    .addOnFailureListener { e -> callback.onError(e.message) }
            }
        }.addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Data Fetching ---

    fun getCAList(callback: DataCallback<List<UserModel>>) {
        val cacheKey = "ca_list_page0"
        val ttl = 60_000L
        if (isCacheValid(cacheKey, ttl)) {
            respondFromCache(cacheKey, object : DataCallback<List<UserModel>> {
                override fun onSuccess(data: List<UserModel>?) {
                    callback.onSuccess(data)
                }

                override fun onError(error: String?) {}
            })
            return
        }
        getCaPage(50, null, object : DataCallback<PageResult<UserModel>> {
            override fun onSuccess(data: PageResult<UserModel>?) {
                if (data != null) {
                    cache[cacheKey] = CacheEntry(data.items, System.currentTimeMillis())
                    callback.onSuccess(data.items)
                } else {
                    callback.onSuccess(emptyList())
                }
            }

            override fun onError(error: String?) {
                callback.onError(error)
            }
        })
    }

    fun getCaPage(limit: Int, lastSnapshot: DocumentSnapshot?, callback: DataCallback<PageResult<UserModel>>) {
        var query: Query = firestore.collection("users")
            .whereEqualTo("role", "CA")
            .orderBy("rating", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        query.get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val caList: MutableList<UserModel> = ArrayList()
                for (document in queryDocumentSnapshots.documents) {
                    val user = document.toObject(UserModel::class.java)
                    if (user != null) caList.add(user)
                }
                val lastDoc = if (queryDocumentSnapshots.documents.isEmpty()) null else queryDocumentSnapshots.documents[queryDocumentSnapshots.size() - 1]
                callback.onSuccess(PageResult(caList, lastDoc))
            }
            .addOnFailureListener {
                // Fallback if index not ready
                var fallback: Query = firestore.collection("users")
                    .whereEqualTo("role", "CA")
                    .limit(limit.toLong())
                if (lastSnapshot != null) {
                    fallback = fallback.startAfter(lastSnapshot)
                }
                fallback.get()
                    .addOnSuccessListener { q ->
                        val caList: MutableList<UserModel> = ArrayList()
                        for (document in q.documents) {
                            val user = document.toObject(UserModel::class.java)
                            if (user != null) caList.add(user)
                        }
                        caList.sortWith { o1, o2 -> java.lang.Double.compare(o2.rating, o1.rating) }
                        val lastDoc = if (q.documents.isEmpty()) null else q.documents[q.documents.size - 1]
                        callback.onSuccess(PageResult(caList, lastDoc))
                    }
                    .addOnFailureListener { err -> callback.onError(err.message) }
            }
    }

    fun createConversation(conversation: ConversationModel, callback: DataCallback<Void?>) {
        firestore.collection("conversations").document(conversation.conversationId!!).set(conversation)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Chat ---

    fun getChatId(uid1: String, uid2: String): String {
        return if (uid1.compareTo(uid2) < 0) {
            uid1 + "_" + uid2
        } else {
            uid2 + "_" + uid1
        }
    }

    fun sendMessage(message: MessageModel, callback: DataCallback<Void?>) {
        val chatId = getChatId(message.senderId!!, message.receiverId!!)
        message.chatId = chatId

        firestore.collection("conversations").document(chatId).collection("messages")
            .add(message)
            .addOnSuccessListener {
                updateConversation(message)
                callback.onSuccess(null)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun updateMessage(message: MessageModel, callback: DataCallback<Void?>) {
        if (message.id == null) {
            callback.onError("Message ID is null")
            return
        }

        val updates: MutableMap<String, Any> = HashMap()
        if (message.message != null) updates["message"] = message.message!!
        if (message.proposalStatus != null) updates["proposalStatus"] = message.proposalStatus!!
        if (message.proposalAmount != null) updates["proposalAmount"] = message.proposalAmount!!
        if (message.proposalDescription != null) updates["proposalDescription"] = message.proposalDescription!!
        if (message.proposalAdvanceAmount != null) updates["proposalAdvanceAmount"] = message.proposalAdvanceAmount!!
        if (message.proposalFinalAmount != null) updates["proposalFinalAmount"] = message.proposalFinalAmount!!
        if (message.proposalAdvancePaid != null) updates["proposalAdvancePaid"] = message.proposalAdvancePaid!!
        if (message.proposalFinalPaid != null) updates["proposalFinalPaid"] = message.proposalFinalPaid!!
        if (message.proposalPaymentStage != null) updates["proposalPaymentStage"] = message.proposalPaymentStage!!
        if (message.proposalRejectionReason != null) updates["proposalRejectionReason"] = message.proposalRejectionReason!!
        if (message.proposalVersion != null) updates["proposalVersion"] = message.proposalVersion!!
        if (message.proposalAdvancePaymentRef != null) updates["proposalAdvancePaymentRef"] = message.proposalAdvancePaymentRef!!
        if (message.proposalFinalPaymentRef != null) updates["proposalFinalPaymentRef"] = message.proposalFinalPaymentRef!!

        if (message.paymentRequestStatus != null) updates["paymentRequestStatus"] = message.paymentRequestStatus!!
        if (message.paymentRequestAmount != null) updates["paymentRequestAmount"] = message.paymentRequestAmount!!
        if (message.paymentRequestTotal != null) updates["paymentRequestTotal"] = message.paymentRequestTotal!!
        if (message.paymentRequestInstallment != null) updates["paymentRequestInstallment"] = message.paymentRequestInstallment!!
        if (message.paymentDeclineReason != null) updates["paymentDeclineReason"] = message.paymentDeclineReason!!
        if (message.paymentStage != null) updates["paymentStage"] = message.paymentStage!!

        firestore.collection("conversations").document(message.chatId!!)
            .collection("messages").document(message.id!!)
            .update(updates)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun submitRating(rating: RatingModel, chatId: String, callback: DataCallback<Void?>) {
        val caId = rating.caId ?: return
        
        firestore.runTransaction { transaction ->
            // 1. Save rating to CA's ratings subcollection
            val ratingRef = firestore.collection("users").document(caId).collection("ratings").document()
            transaction.set(ratingRef, rating)

            // 2. Update CA's overall rating
            val caRef = firestore.collection("users").document(caId)
            val caSnapshot = transaction.get(caRef)
            
            val currentRating = caSnapshot.getDouble("rating") ?: 0.0
            val currentCount = caSnapshot.getLong("ratingCount") ?: 0
            
            val newCount = currentCount + 1
            val newRating = (currentRating * currentCount + rating.rating) / newCount
            
            transaction.update(caRef, "rating", newRating)
            transaction.update(caRef, "ratingCount", newCount.toInt()) // Cast to Int for UserModel compatibility

            // 3. Reset conversation workflow state to Discussion
            val convRef = firestore.collection("conversations").document(chatId)
            transaction.update(convRef, "workflowState", ConversationModel.STATE_DISCUSSION)
            
            null
        }.addOnSuccessListener {
            callback.onSuccess(null)
        }.addOnFailureListener { e ->
            callback.onError(e.message)
        }
    }

    private fun updateConversation(message: MessageModel) {
        val chatId = message.chatId
        val participants = listOf(message.senderId, message.receiverId)

        val updates: MutableMap<String, Any> = HashMap()
        updates["conversationId"] = chatId!!
        updates["participantIds"] = participants

        var lastMsg = message.message
        if ("DOCUMENT" == message.type) {
            lastMsg = "📄 " + (if (lastMsg != null) lastMsg else "Document")
        } else if (message.imageUrl != null && message.imageUrl!!.isNotEmpty()) {
            lastMsg = "📷 Image"
        } else if ("PROPOSAL" == message.type) {
            lastMsg = "Proposal: " + message.proposalDescription
        }

        updates["lastMessage"] = lastMsg ?: ""
        updates["lastMessageTimestamp"] = message.timestamp

        // Increment unread count for receiver
        val receiverId = message.receiverId
        if (receiverId != null) {
            updates["unreadCounts.$receiverId"] = FieldValue.increment(1)
        }

        firestore.collection("conversations").document(chatId)
            .set(updates, SetOptions.merge())
    }

    fun markMessagesAsRead(chatId: String, userId: String) {
        val conversationRef = firestore.collection("conversations").document(chatId)
        
        // 1. Reset unread count for this user
        conversationRef.update("unreadCounts.$userId", 0)
            .addOnFailureListener { _ -> /* Log error */ }

        // 2. Mark received messages as read
        conversationRef.collection("messages")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("isRead", false)
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                for (doc in snapshot.documents) {
                    batch.update(doc.reference, "isRead", true)
                }
                batch.commit()
            }
    }

    fun listenToUnreadCount(userId: String, callback: DataCallback<Int>): ListenerRegistration {
        return firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    callback.onError(e.message)
                    return@addSnapshotListener
                }

                var totalUnread = 0
                if (snapshots != null) {
                    for (doc in snapshots.documents) {
                        val conv = doc.toObject(ConversationModel::class.java)
                        if (conv != null) {
                            val counts = conv.unreadCounts
                            if (counts != null && counts.containsKey(userId)) {
                                totalUnread += counts[userId] ?: 0
                            }
                        }
                    }
                }
                callback.onSuccess(totalUnread)
            }
    }

    fun getConversations(userId: String, callback: DataCallback<List<ConversationModel>>) {
        firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            .orderBy("lastMessageTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    callback.onError(e.message)
                    return@addSnapshotListener
                }

                val conversations: MutableList<ConversationModel> = ArrayList()
                if (snapshots != null) {
                    for (doc in snapshots.documents) {
                        val conv = doc.toObject(ConversationModel::class.java)
                        // Filter out requests/refused conversations from the main chat list
                        if (conv != null && ConversationModel.STATE_REQUESTED != conv.workflowState &&
                            ConversationModel.STATE_REFUSED != conv.workflowState
                        ) {
                            conversations.add(conv)
                        }
                    }
                }

                if (conversations.isEmpty()) {
                    callback.onSuccess(conversations)
                    return@addSnapshotListener
                }

                // Fetch user details
                populateUserDetails(userId, conversations, callback)
            }
    }

    fun getRequests(userId: String, callback: DataCallback<List<ConversationModel>>) {
        firestore.collection("conversations")
            .whereArrayContains("participantIds", userId)
            //.whereEqualTo("workflowState", ConversationModel.STATE_REQUESTED) // Avoid composite index requirement
            //.orderBy("lastMessageTimestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    callback.onError(e.message)
                    return@addSnapshotListener
                }

                val conversations: MutableList<ConversationModel> = ArrayList()
                if (snapshots != null) {
                    for (doc in snapshots.documents) {
                        val conv = doc.toObject(ConversationModel::class.java)
                        if (conv != null && ConversationModel.STATE_REQUESTED == conv.workflowState) {
                            conversations.add(conv)
                        }
                    }
                }

                if (conversations.isEmpty()) {
                    callback.onSuccess(conversations)
                    return@addSnapshotListener
                }

                populateUserDetails(userId, conversations, callback)
            }
    }

    private fun populateUserDetails(
        userId: String,
        conversations: List<ConversationModel>,
        callback: DataCallback<List<ConversationModel>>
    ) {
        val tasks: MutableList<Task<DocumentSnapshot>> = ArrayList()
        for (conv in conversations) {
            var otherId: String? = null
            if (conv.participantIds != null) {
                for (pid in conv.participantIds!!) {
                    if (pid != userId) {
                        otherId = pid
                        break
                    }
                }
            }
            if (otherId != null) {
                tasks.add(firestore.collection("users").document(otherId).get())
            }
        }

        if (tasks.isEmpty()) {
            callback.onSuccess(conversations)
            return
        }

        Tasks.whenAllSuccess<DocumentSnapshot>(tasks).addOnSuccessListener { objects ->
            for (obj in objects) {
                val userDoc = obj
                if (userDoc.exists()) {
                    val user = userDoc.toObject(UserModel::class.java)
                    if (user != null) {
                        if (user.uid == null) user.uid = userDoc.id
                        for (conv in conversations) {
                            if (conv.participantIds != null && conv.participantIds!!.contains(user.uid)) {
                                conv.otherUserName = user.name
                                conv.otherUserEmail = user.email
                                conv.otherUserProfileImage = user.profileImageUrl
                            }
                        }
                    }
                }
            }
            callback.onSuccess(conversations)
        }.addOnFailureListener {
            callback.onSuccess(conversations)
        }
    }

    fun sendRequest(senderId: String, receiverId: String, initialMessageText: String, callback: DataCallback<Void?>) {
        val chatId = getChatId(senderId, receiverId)
        firestore.collection("conversations").document(chatId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    val existing = snapshot.toObject(ConversationModel::class.java)
                    val state = existing?.workflowState
                    if (ConversationModel.STATE_REQUESTED == state) {
                        val updates: MutableMap<String, Any> = HashMap()
                        updates["lastMessage"] = initialMessageText
                        updates["lastMessageTimestamp"] = System.currentTimeMillis()
                        firestore.collection("conversations").document(chatId).update(updates)
                            .addOnSuccessListener { callback.onSuccess(null) }
                            .addOnFailureListener { e -> callback.onError(e.message) }
                        return@addOnSuccessListener
                    }
                    if (existing == null || ConversationModel.STATE_REFUSED == state || ConversationModel.STATE_COMPLETED == state || state == null) {
                        startNewServiceCycle(chatId, senderId, receiverId, initialMessageText, existing, callback)
                        return@addOnSuccessListener
                    }
                    sendMessage(
                        MessageModel(
                            senderId,
                            receiverId,
                            chatId,
                            initialMessageText,
                            System.currentTimeMillis(),
                            "TEXT"
                        ), callback
                    )
                } else {
                    startNewServiceCycle(chatId, senderId, receiverId, initialMessageText, null, callback)
                }
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    private fun startNewServiceCycle(
        chatId: String,
        senderId: String,
        receiverId: String,
        initialMessageText: String,
        existing: ConversationModel?,
        callback: DataCallback<Void?>
    ) {
        val cycleId = UUID.randomUUID().toString()
        val nextSequence = if (existing != null) existing.serviceCycleSequence + 1 else 1
        val now = System.currentTimeMillis()

        val conversationRef = firestore.collection("conversations").document(chatId)
        val cycleRef = conversationRef.collection("serviceCycles").document(cycleId)

        val conversationUpdates: MutableMap<String, Any> = HashMap()
        conversationUpdates["conversationId"] = chatId
        conversationUpdates["participantIds"] = listOf(senderId, receiverId)
        conversationUpdates["workflowState"] = ConversationModel.STATE_REQUESTED
        conversationUpdates["lastMessage"] = initialMessageText
        conversationUpdates["lastMessageTimestamp"] = now
        conversationUpdates["currentServiceCycleId"] = cycleId
        conversationUpdates["serviceCycleSequence"] = nextSequence

        val cycleData: MutableMap<String, Any> = HashMap()
        cycleData["cycleId"] = cycleId
        cycleData["sequence"] = nextSequence
        cycleData["status"] = "REQUESTED"
        cycleData["createdAt"] = now
        cycleData["requestedBy"] = senderId
        cycleData["caId"] = receiverId
        cycleData["customerId"] = senderId
        cycleData["lastStatus"] = ConversationModel.STATE_REQUESTED
        cycleData["lastStatusAt"] = now
        cycleData["pendingChecks"] = ArrayList<Any>()
        val timeline: MutableList<Map<String, Any>> = ArrayList()
        val entry: MutableMap<String, Any> = HashMap()
        entry["state"] = ConversationModel.STATE_REQUESTED
        entry["timestamp"] = now
        timeline.add(entry)
        cycleData["statusTimeline"] = timeline

        val batch = firestore.batch()
        if (existing == null) {
            batch.set(conversationRef, conversationUpdates)
        } else {
            batch.set(conversationRef, conversationUpdates, SetOptions.merge())
        }
        batch.set(cycleRef, cycleData, SetOptions.merge())
        batch.commit()
            .addOnSuccessListener {
                if (nextSequence > 1) {
                    val details: MutableMap<String, Any> = HashMap()
                    details["chatId"] = chatId
                    details["cycleId"] = cycleId
                    details["sequence"] = nextSequence
                    details["requesterId"] = senderId
                    details["caId"] = receiverId
                    AnalyticsRepository.getInstance()?.log("repeat_service_request", details)
                }
                callback.onSuccess(null)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun appendServiceCycleStatus(chatId: String, state: String, note: String?, callback: DataCallback<Void?>?) {
        firestore.collection("conversations").document(chatId).get()
            .addOnSuccessListener { snapshot ->
                val cycleId = if (snapshot.contains("currentServiceCycleId")) snapshot.getString("currentServiceCycleId") else null
                if (cycleId == null || cycleId.trim { it <= ' ' }.isEmpty()) {
                    callback?.onSuccess(null)
                    return@addOnSuccessListener
                }
                val now = System.currentTimeMillis()
                val entry: MutableMap<String, Any> = HashMap()
                entry["state"] = state
                entry["timestamp"] = now
                if (note != null && !note.trim { it <= ' ' }.isEmpty()) {
                    entry["note"] = note
                }
                firestore.collection("conversations").document(chatId).collection("serviceCycles").document(cycleId)
                    .update(
                        "lastStatus", state,
                        "lastStatusAt", now,
                        "statusTimeline", FieldValue.arrayUnion(entry)
                    )
                    .addOnSuccessListener { callback?.onSuccess(null) }
                    .addOnFailureListener { e -> callback?.onError(e.message) }
            }
            .addOnFailureListener { e -> callback?.onError(e.message) }
    }

    fun completeServiceCycle(chatId: String, summary: Map<String, Any>?, callback: DataCallback<Void?>?) {
        firestore.collection("conversations").document(chatId).get()
            .addOnSuccessListener { snapshot ->
                var cycleId = if (snapshot.contains("currentServiceCycleId")) snapshot.getString("currentServiceCycleId") else null
                if (cycleId == null || cycleId.trim { it <= ' ' }.isEmpty()) {
                    cycleId = UUID.randomUUID().toString()
                }
                val now = System.currentTimeMillis()
                val updates: MutableMap<String, Any> = HashMap()
                if (summary != null) {
                    updates.putAll(summary)
                }
                updates["status"] = "COMPLETED"
                updates["completedAt"] = now
                updates["lastStatus"] = ConversationModel.STATE_COMPLETED
                updates["lastStatusAt"] = now

                val batch = firestore.batch()
                val conversationRef = firestore.collection("conversations").document(chatId)
                val cycleRef = conversationRef.collection("serviceCycles").document(cycleId!!)
                batch.set(cycleRef, updates, SetOptions.merge())
                val conversationUpdates: MutableMap<String, Any> = HashMap()
                conversationUpdates["lastServiceCompletedAt"] = now
                batch.set(conversationRef, conversationUpdates, SetOptions.merge())
                batch.commit()
                    .addOnSuccessListener { callback?.onSuccess(null) }
                    .addOnFailureListener { e -> callback?.onError(e.message) }
            }
            .addOnFailureListener { e -> callback?.onError(e.message) }
    }

    fun getServiceHistory(chatId: String, callback: DataCallback<List<Map<String, Any>>>) {
        firestore.collection("conversations").document(chatId).collection("serviceCycles")
            .orderBy("completedAt", Query.Direction.DESCENDING)
            .limit(10)
            .get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val items: MutableList<Map<String, Any>> = ArrayList()
                for (doc in queryDocumentSnapshots.documents) {
                    val data = doc.data
                    if (data != null) {
                        items.add(data)
                    }
                }
                callback.onSuccess(items)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun listenForMessages(chatId: String, callback: DataCallback<List<MessageModel>>): ListenerRegistration {
        return listenForRecentMessages(chatId, 50, callback)
    }

    fun listenForRecentMessages(
        chatId: String,
        limit: Int,
        callback: DataCallback<List<MessageModel>>
    ): ListenerRegistration {
        return firestore.collection("conversations").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(limit.toLong())
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    callback.onError(e.message)
                    return@addSnapshotListener
                }

                val messages: MutableList<MessageModel> = ArrayList()
                if (snapshots != null) {
                    for (doc in snapshots.documents) {
                        val msg = doc.toObject(MessageModel::class.java)
                        if (msg != null) {
                            msg.id = doc.id
                            messages.add(msg)
                        }
                    }
                }
                callback.onSuccess(messages)
            }
    }

    fun getMessagesPage(
        chatId: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?,
        callback: DataCallback<PageResult<MessageModel>>
    ) {
        var query: Query = firestore.collection("conversations").document(chatId).collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        query.get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val messages: MutableList<MessageModel> = ArrayList()
                for (doc in queryDocumentSnapshots.documents) {
                    val msg = doc.toObject(MessageModel::class.java)
                    if (msg != null) {
                        msg.id = doc.id
                        messages.add(msg)
                    }
                }
                messages.reverse()
                val lastDoc = if (queryDocumentSnapshots.documents.isEmpty()) null else queryDocumentSnapshots.documents[queryDocumentSnapshots.size() - 1]
                callback.onSuccess(PageResult(messages, lastDoc))
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun listenToConversation(chatId: String, callback: DataCallback<ConversationModel?>): ListenerRegistration {
        return firestore.collection("conversations").document(chatId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    callback.onError(e.message)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val conversation = snapshot.toObject(ConversationModel::class.java)
                    callback.onSuccess(conversation)
                } else {
                    callback.onSuccess(null)
                }
            }
    }

    fun updateConversationState(chatId: String, state: String, callback: DataCallback<Void?>) {
        val ref = firestore.collection("conversations").document(chatId)
        updateFieldWithRetry(ref, "workflowState", state, 2, callback)
    }

    fun updateVideoCallPermission(chatId: String, allowed: Boolean, callback: DataCallback<Void?>) {
        val ref = firestore.collection("conversations").document(chatId)
        updateFieldWithRetry(ref, "videoCallAllowed", allowed, 2, callback)
    }

    fun acceptRequest(request: ConversationModel, callback: DataCallback<Void?>) {
        if (firebaseAuth.currentUser == null) {
            callback.onError("User not authenticated")
            return
        }

        val currentUserId = firebaseAuth.currentUser!!.uid
        val chatId = request.conversationId!!
        val initialMessageText = request.lastMessage!!

        // Robust sender identification: The sender of the request is the OTHER participant (not the current CA)
        var msgSenderId: String? = null
        if (request.participantIds != null) {
            for (pid in request.participantIds!!) {
                if (pid != currentUserId) {
                    msgSenderId = pid
                    break
                }
            }
        }

        // Fallback if logic fails (e.g. testing with self)
        if (msgSenderId == null) {
            if (request.participantIds != null && request.participantIds!!.isNotEmpty()) {
                msgSenderId = request.participantIds!![0]
            } else {
                callback.onError("No participants found")
                return
            }
        }

        val msgReceiverId = currentUserId

        val finalMsgSenderId = msgSenderId!!
        val msg = MessageModel(
            finalMsgSenderId,
            msgReceiverId,
            chatId,
            initialMessageText,
            System.currentTimeMillis(),
            "TEXT"
        )

        // 1. Update State to DISCUSSION
        // 2. Add Message to subcollection

        firestore.collection("conversations").document(chatId)
            .update("workflowState", ConversationModel.STATE_DISCUSSION)
            .addOnSuccessListener {
                incrementClientCount(currentUserId, finalMsgSenderId) // Use the new implementation
                firestore.collection("conversations").document(chatId).collection("messages").add(msg)
                    .addOnSuccessListener { callback.onSuccess(null) }
                    .addOnFailureListener { e -> callback.onError("State updated but message failed: " + e.message) }
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun updateProposalStatus(
        chatId: String,
        messageId: String,
        status: String,
        rejectionReason: String?,
        callback: DataCallback<Void?>
    ) {
        val updates: MutableMap<String, Any> = HashMap()
        updates["proposalStatus"] = status
        if (rejectionReason != null && !rejectionReason.trim { it <= ' ' }.isEmpty()) {
            updates["proposalRejectionReason"] = rejectionReason.trim { it <= ' ' }
        }
        firestore.collection("conversations").document(chatId).collection("messages").document(messageId)
            .update(updates)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun getRevenueStats(userId: String, callback: DataCallback<Double>) {
        firestore.collection("transactions")
            .whereEqualTo("caId", userId)
            .whereEqualTo("status", "SUCCESS")
            .get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                var total = 0.0
                for (doc in queryDocumentSnapshots) {
                    val amount = doc.getDouble("amount") ?: 0.0
                    total += amount
                }
                callback.onSuccess(total)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun getRevenueBreakdown(userId: String, callback: DataCallback<RevenueBreakdown>) {
        firestore.collection("transactions")
            .whereEqualTo("caId", userId)
            .whereEqualTo("status", "SUCCESS")
            .get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val now = Calendar.getInstance()
                val startOfDay = now.clone() as Calendar
                startOfDay[Calendar.HOUR_OF_DAY] = 0
                startOfDay[Calendar.MINUTE] = 0
                startOfDay[Calendar.SECOND] = 0
                startOfDay[Calendar.MILLISECOND] = 0

                val startOfWeek = now.clone() as Calendar
                startOfWeek[Calendar.DAY_OF_WEEK] = startOfWeek.firstDayOfWeek
                startOfWeek[Calendar.HOUR_OF_DAY] = 0
                startOfWeek[Calendar.MINUTE] = 0
                startOfWeek[Calendar.SECOND] = 0
                startOfWeek[Calendar.MILLISECOND] = 0

                val startOfMonth = now.clone() as Calendar
                startOfMonth[Calendar.DAY_OF_MONTH] = 1
                startOfMonth[Calendar.HOUR_OF_DAY] = 0
                startOfMonth[Calendar.MINUTE] = 0
                startOfMonth[Calendar.SECOND] = 0
                startOfMonth[Calendar.MILLISECOND] = 0

                val dayStartMs = startOfDay.timeInMillis
                val weekStartMs = startOfWeek.timeInMillis
                val monthStartMs = startOfMonth.timeInMillis

                var todayTotal = 0.0
                var weekTotal = 0.0
                var monthTotal = 0.0
                for (doc in queryDocumentSnapshots) {
                    val amount = doc.getDouble("amount")
                    val ts = doc.getTimestamp("timestamp")
                    if (amount == null || ts == null) continue
                    val ms = ts.toDate().time
                    if (ms >= weekStartMs) {
                        weekTotal += amount
                    }
                    if (ms >= monthStartMs) {
                        monthTotal += amount
                        if (ms >= dayStartMs) {
                            todayTotal += amount
                        }
                    }
                }
                callback.onSuccess(RevenueBreakdown(todayTotal, weekTotal, monthTotal))
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Transactions ---

    fun createTransaction(transaction: TransactionModel, callback: DataCallback<Void?>) {
        firestore.collection("transactions").document(transaction.transactionId!!).set(transaction)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun getTransactions(userId: String, callback: DataCallback<List<TransactionModel>>) {
        getTransactionsPage(userId, 50, null, object : DataCallback<PageResult<TransactionModel>> {
            override fun onSuccess(data: PageResult<TransactionModel>?) {
                if (data != null) {
                    callback.onSuccess(data.items)
                } else {
                    callback.onSuccess(emptyList())
                }
            }

            override fun onError(error: String?) {
                callback.onError(error)
            }
        })
    }

    fun getTransactionsPage(
        userId: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?,
        callback: DataCallback<PageResult<TransactionModel>>
    ) {
        var query: Query = firestore.collection("transactions")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }

        query.get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val transactions: MutableList<TransactionModel> = ArrayList()
                for (doc in queryDocumentSnapshots.documents) {
                    val t = doc.toObject(TransactionModel::class.java)
                    if (t != null) transactions.add(t)
                }
                val lastDoc = if (queryDocumentSnapshots.documents.isEmpty()) null else queryDocumentSnapshots.documents[queryDocumentSnapshots.size() - 1]
                callback.onSuccess(PageResult(transactions, lastDoc))
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun updateWalletBalance(userId: String, amount: Double, callback: DataCallback<Double>) {
        val userRef = firestore.collection("users").document(userId)
        
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(userRef)
            if (!snapshot.exists()) {
                throw Exception("User not found")
            }
            
            val currentBalance = snapshot.getDouble("walletBalance") ?: 0.0
            val newBalance = currentBalance + amount
            
            if (newBalance < 0) {
                throw Exception("Insufficient balance")
            }
            
            transaction.update(userRef, "walletBalance", newBalance)
            newBalance
        }.addOnSuccessListener { newBalance ->
            callback.onSuccess(newBalance)
        }.addOnFailureListener { e ->
            callback.onError(e.message)
        }
    }

    fun getWalletBalance(userId: String, callback: DataCallback<Double>) {
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { documentSnapshot ->
                if (documentSnapshot.exists()) {
                    val user = documentSnapshot.toObject(UserModel::class.java)
                    if (user != null) {
                        callback.onSuccess(user.walletBalance)
                    } else {
                        callback.onSuccess(0.0)
                    }
                } else {
                    callback.onError("User not found")
                }
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Bookings ---

    fun saveBooking(booking: BookingModel, callback: DataCallback<Void?>) {
        val id = if (booking.id != null) booking.id else firestore.collection("bookings").document().id
        booking.id = id
        firestore.collection("bookings").document(id!!)
            .set(booking)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun getBookingsForUser(userId: String, callback: DataCallback<List<BookingModel>>) {
        getBookingsForUserPage(userId, 50, null, object : DataCallback<PageResult<BookingModel>> {
            override fun onSuccess(data: PageResult<BookingModel>?) {
                if (data != null) {
                    callback.onSuccess(data.items)
                } else {
                    callback.onSuccess(emptyList())
                }
            }

            override fun onError(error: String?) {
                callback.onError(error)
            }
        })
    }

    fun getBookingsForCA(caId: String, callback: DataCallback<List<BookingModel>>) {
        getBookingsForCaPage(caId, 50, null, object : DataCallback<PageResult<BookingModel>> {
            override fun onSuccess(data: PageResult<BookingModel>?) {
                if (data != null) {
                    callback.onSuccess(data.items)
                } else {
                    callback.onSuccess(emptyList())
                }
            }

            override fun onError(error: String?) {
                callback.onError(error)
            }
        })
    }

    fun getBookingsForUserPage(
        userId: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?,
        callback: DataCallback<PageResult<BookingModel>>
    ) {
        var query: Query = firestore.collection("bookings")
            .whereEqualTo("userId", userId)
            .orderBy("appointmentTimestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }

        query.get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val list: MutableList<BookingModel> = ArrayList()
                for (doc in queryDocumentSnapshots.documents) {
                    val bm = doc.toObject(BookingModel::class.java)
                    if (bm != null) list.add(bm)
                }
                val lastDoc = if (queryDocumentSnapshots.documents.isEmpty()) null else queryDocumentSnapshots.documents[queryDocumentSnapshots.size() - 1]
                callback.onSuccess(PageResult(list, lastDoc))
            }
            .addOnFailureListener { e ->
                val msg = if (e.message != null) e.message else ""
                if (msg!!.contains("index") || msg.contains("FAILED_PRECONDITION")) {
                    val fallback: Query = firestore.collection("bookings")
                        .whereEqualTo("userId", userId)
                        .limit(limit.toLong())
                    fallback.get()
                        .addOnSuccessListener { fallbackSnapshots ->
                            val list: MutableList<BookingModel> = ArrayList()
                            for (doc in fallbackSnapshots.documents) {
                                val bm = doc.toObject(BookingModel::class.java)
                                if (bm != null) list.add(bm)
                            }
                            list.sortWith { a, b ->
                                java.lang.Long.compare(
                                    b.appointmentTimestamp,
                                    a.appointmentTimestamp
                                )
                            }
                            callback.onSuccess(PageResult(list, null))
                        }
                        .addOnFailureListener { fallbackErr -> callback.onError(fallbackErr.message) }
                } else {
                    callback.onError(msg)
                }
            }
    }

    fun getBookingsForCaPage(
        caId: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?,
        callback: DataCallback<PageResult<BookingModel>>
    ) {
        var query: Query = firestore.collection("bookings")
            .whereEqualTo("caId", caId)
            .orderBy("appointmentTimestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }

        query.get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val list: MutableList<BookingModel> = ArrayList()
                for (doc in queryDocumentSnapshots.documents) {
                    val bm = doc.toObject(BookingModel::class.java)
                    if (bm != null) list.add(bm)
                }
                val lastDoc = if (queryDocumentSnapshots.documents.isEmpty()) null else queryDocumentSnapshots.documents[queryDocumentSnapshots.size() - 1]
                populateBookingUserDetails(list, object : DataCallback<List<BookingModel>> {
                    override fun onSuccess(data: List<BookingModel>?) {
                        callback.onSuccess(PageResult(data ?: list, lastDoc))
                    }
                    override fun onError(error: String?) {
                        callback.onSuccess(PageResult(list, lastDoc))
                    }
                })
            }
            .addOnFailureListener { e ->
                val msg = if (e.message != null) e.message else ""
                if (msg!!.contains("index") || msg.contains("FAILED_PRECONDITION")) {
                    val fallback: Query = firestore.collection("bookings")
                        .whereEqualTo("caId", caId)
                        .limit(limit.toLong())
                    fallback.get()
                        .addOnSuccessListener { fallbackSnapshots ->
                            val list: MutableList<BookingModel> = ArrayList()
                            for (doc in fallbackSnapshots.documents) {
                                val bm = doc.toObject(BookingModel::class.java)
                                if (bm != null) list.add(bm)
                            }
                            list.sortWith { a, b ->
                                java.lang.Long.compare(
                                    b.appointmentTimestamp,
                                    a.appointmentTimestamp
                                )
                            }
                            populateBookingUserDetails(list, object : DataCallback<List<BookingModel>> {
                                override fun onSuccess(data: List<BookingModel>?) {
                                    callback.onSuccess(PageResult(data ?: list, null))
                                }
                                override fun onError(error: String?) {
                                    callback.onSuccess(PageResult(list, null))
                                }
                            })
                        }
                        .addOnFailureListener { fallbackErr -> callback.onError(fallbackErr.message) }
                } else {
                    callback.onError(msg)
                }
            }
    }

    private fun populateBookingUserDetails(
        bookings: List<BookingModel>,
        callback: DataCallback<List<BookingModel>>
    ) {
        val tasks: MutableList<Task<DocumentSnapshot>> = ArrayList()
        for (booking in bookings) {
            val userId = booking.userId
            if (userId != null) {
                tasks.add(firestore.collection("users").document(userId).get())
            }
        }

        if (tasks.isEmpty()) {
            callback.onSuccess(bookings)
            return
        }

        Tasks.whenAllSuccess<DocumentSnapshot>(tasks).addOnSuccessListener { objects ->
            for (obj in objects) {
                val userDoc = obj
                if (userDoc.exists()) {
                    val user = userDoc.toObject(UserModel::class.java)
                    if (user != null) {
                        val uid = user.uid ?: userDoc.id
                        for (booking in bookings) {
                            if (booking.userId == uid) {
                                booking.userName = user.name
                                // Note: BookingModel doesn't have a userProfileImage field, 
                                // but we could add it if needed. For now, userName is enough 
                                // as RequestsActivity.enrichRequestsWithUserData will fetch the full UserModel.
                            }
                        }
                    }
                }
            }
            callback.onSuccess(bookings)
        }.addOnFailureListener {
            callback.onSuccess(bookings)
        }
    }

    fun incrementClientCount(caId: String, userId: String) {
        // First check if already counted to avoid double counting
        firestore.collection("users").document(caId).collection("clients").document(userId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    val batch = firestore.batch()
                    
                    // 1. Mark as client
                    val clientRef = firestore.collection("users").document(caId).collection("clients").document(userId)
                    batch.set(clientRef, mapOf("timestamp" to System.currentTimeMillis()))
                    
                    // 2. Increment count in CA profile
                    val caRef = firestore.collection("users").document(caId)
                    batch.update(caRef, "clientCount", FieldValue.increment(1))
                    
                    batch.commit()
                }
            }
    }

    fun isReturningClient(caId: String, userId: String, callback: DataCallback<Boolean>) {
        firestore.collection("users").document(caId).collection("clients").document(userId).get()
            .addOnSuccessListener { snapshot ->
                callback.onSuccess(snapshot.exists())
            }
            .addOnFailureListener { e ->
                callback.onError(e.message)
            }
    }

    fun updateBookingStatus(bookingId: String, status: String, callback: DataCallback<Void?>) {
        val ref = firestore.collection("bookings").document(bookingId)
        updateFieldWithRetry(ref, "status", status, 2, callback)
    }

    fun blockUser(currentUserId: String, targetUserId: String, callback: DataCallback<Void?>) {
        firestore.collection("users").document(currentUserId)
            .update("blockedUsers", FieldValue.arrayUnion(targetUserId))
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Transactions ---

    fun getCaTransactions(caId: String, callback: DataCallback<List<TransactionModel>>) {
        firestore.collection("transactions")
            .whereEqualTo("caId", caId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val transactions: MutableList<TransactionModel> = ArrayList()
                for (doc in queryDocumentSnapshots) {
                    val t = doc.toObject(TransactionModel::class.java)
                    transactions.add(t)
                }
                callback.onSuccess(transactions)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }


    // --- Community Forum ---

    fun createPost(post: PostModel, callback: DataCallback<Void?>) {
        firestore.collection("posts").document(post.id!!).set(post)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun toggleLike(postId: String, userId: String, callback: DataCallback<Boolean>) {
        val postRef = firestore.collection("posts").document(postId)
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(postRef)
            if (!snapshot.exists()) return@runTransaction null

            val post = snapshot.toObject(PostModel::class.java) ?: return@runTransaction null

            val likedBy = post.likedBy

            val isLiked: Boolean
            if (likedBy.contains(userId)) {
                likedBy.remove(userId)
                isLiked = false
            } else {
                likedBy.add(userId)
                isLiked = true
            }

            transaction.update(postRef, "likedBy", likedBy, "likeCount", likedBy.size)
            isLiked
        }.addOnSuccessListener { isLiked ->
            if (isLiked != null) {
                callback.onSuccess(isLiked)
            } else {
                callback.onError("Post not found")
            }
        }.addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun getPosts(callback: DataCallback<List<PostModel>>) {
        val cacheKey = "posts_page0"
        val ttl = 45_000L
        if (isCacheValid(cacheKey, ttl)) {
            respondFromCache(cacheKey, object : DataCallback<List<PostModel>> {
                override fun onSuccess(data: List<PostModel>?) {
                    callback.onSuccess(data)
                }

                override fun onError(error: String?) {}
            })
            return
        }
        getPostsPage(25, null, object : DataCallback<PageResult<PostModel>> {
            override fun onSuccess(data: PageResult<PostModel>?) {
                if (data != null) {
                    cache[cacheKey] = CacheEntry(data.items, System.currentTimeMillis())
                    callback.onSuccess(data.items)
                } else {
                    callback.onSuccess(emptyList())
                }
            }

            override fun onError(error: String?) {
                callback.onError(error)
            }
        })
    }

    fun getPostsPage(limit: Int, lastSnapshot: DocumentSnapshot?, callback: DataCallback<PageResult<PostModel>>) {
        var query: Query = firestore.collection("posts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        query.get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val list: MutableList<PostModel> = ArrayList()
                for (doc in queryDocumentSnapshots.documents) {
                    val p = doc.toObject(PostModel::class.java)
                    if (p != null) list.add(p)
                }
                val lastDoc = if (queryDocumentSnapshots.documents.isEmpty()) null else queryDocumentSnapshots.documents[queryDocumentSnapshots.size() - 1]
                callback.onSuccess(PageResult(list, lastDoc))
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Comments ---

    fun getComments(postId: String, callback: DataCallback<List<CommentModel>>) {
        getCommentsPage(postId, 50, null, object : DataCallback<PageResult<CommentModel>> {
            override fun onSuccess(data: PageResult<CommentModel>?) {
                if (data != null) {
                    callback.onSuccess(data.items)
                } else {
                    callback.onSuccess(emptyList())
                }
            }

            override fun onError(error: String?) {
                callback.onError(error)
            }
        })
    }

    fun getCommentsPage(
        postId: String,
        limit: Int,
        lastSnapshot: DocumentSnapshot?,
        callback: DataCallback<PageResult<CommentModel>>
    ) {
        var query: Query = firestore.collection("posts").document(postId).collection("comments")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
        if (lastSnapshot != null) {
            query = query.startAfter(lastSnapshot)
        }
        query.get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val list: MutableList<CommentModel> = ArrayList()
                for (doc in queryDocumentSnapshots.documents) {
                    val c = doc.toObject(CommentModel::class.java)
                    if (c != null) list.add(c)
                }
                list.reverse()
                val lastDoc = if (queryDocumentSnapshots.documents.isEmpty()) null else queryDocumentSnapshots.documents[queryDocumentSnapshots.size() - 1]
                callback.onSuccess(PageResult(list, lastDoc))
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun addComment(postId: String, comment: CommentModel, callback: DataCallback<Void?>) {
        firestore.collection("posts").document(postId).collection("comments").document(comment.id!!)
            .set(comment)
            .addOnSuccessListener {
                // Update comment count
                firestore.collection("posts").document(postId)
                    .update("commentCount", FieldValue.increment(1))
                callback.onSuccess(null)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    // --- Milestones ---

    fun getMilestones(bookingId: String, callback: DataCallback<List<MilestoneModel>>) {
        firestore.collection("bookings").document(bookingId).collection("milestones")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { queryDocumentSnapshots ->
                val list: MutableList<MilestoneModel> = ArrayList()
                for (doc in queryDocumentSnapshots) {
                    val m = doc.toObject(MilestoneModel::class.java)
                    list.add(m)
                }
                callback.onSuccess(list)
            }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }

    fun updateMilestoneStatus(bookingId: String, milestoneId: String, status: String, callback: DataCallback<Void?>) {
        val ref = firestore.collection("bookings").document(bookingId)
            .collection("milestones").document(milestoneId)
        updateFieldWithRetry(ref, "status", status, 2, callback)
    }

    fun addMilestone(bookingId: String, milestone: MilestoneModel, callback: DataCallback<Void?>) {
        firestore.collection("bookings").document(bookingId).collection("milestones").document(milestone.id!!)
            .set(milestone)
            .addOnSuccessListener { callback.onSuccess(null) }
            .addOnFailureListener { e -> callback.onError(e.message) }
    }
}
