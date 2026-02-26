package com.example.taxconnect.data.repositories

import android.content.Context
import androidx.collection.LruCache
import com.example.taxconnect.core.network.NetworkUtils
import com.example.taxconnect.data.models.UserModel
import com.example.taxconnect.data.services.FirestoreSyncWorker
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepository @Inject constructor() {

    companion object {
        @Volatile
        private var instance: UserRepository? = null

        @JvmStatic
        fun getInstance(): UserRepository {
            return instance ?: synchronized(this) {
                instance ?: UserRepository().also { instance = it }
            }
        }
    }

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private data class CacheEntry(val data: Any?, val ts: Long)
    private val cache = LruCache<String, CacheEntry>(50)

    private fun isCacheValid(key: String, ttlMs: Long): Boolean {
        val ce = cache.get(key)
        return ce != null && (System.currentTimeMillis() - ce.ts) < ttlMs
    }

    // --- Authentication ---

    suspend fun loginUser(email: String, password: String): UserModel {
        try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("User ID not found")
            return fetchUser(uid)
        } catch (e: Exception) {
            throw Exception("Login failed: ${e.message}")
        }
    }

    suspend fun registerUser(email: String, password: String, userModel: UserModel) {
        try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val uid = authResult.user?.uid ?: throw Exception("User ID not found")
            userModel.uid = uid
            firestore.collection("users").document(uid).set(userModel).await()
        } catch (e: Exception) {
            throw Exception("Registration failed: ${e.message}")
        }
    }

    // --- User CRUD ---

    suspend fun fetchUser(uid: String): UserModel {
        val cacheKey = "user_$uid"
        if (isCacheValid(cacheKey, 30_000L)) {
            @Suppress("UNCHECKED_CAST")
            val cached = cache.get(cacheKey)?.data as? UserModel
            if (cached != null) return cached
        }

        val snapshot = firestore.collection("users").document(uid).get().await()
        if (snapshot.exists()) {
            val user = snapshot.toObject(UserModel::class.java)
                ?: throw Exception("User data parsing failed")
            if (user.uid == null) user.uid = snapshot.id
            cache.put(cacheKey, CacheEntry(user, System.currentTimeMillis()))
            return user
        } else {
            throw Exception("User not found in database")
        }
    }
    
    fun observeUser(uid: String): Flow<UserModel> = callbackFlow {
        val listenerRegistration = firestore.collection("users").document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(UserModel::class.java)
                    if (user != null) {
                        if (user.uid == null) user.uid = snapshot.id
                        // Update cache
                        cache.put("user_$uid", CacheEntry(user, System.currentTimeMillis()))
                        trySend(user)
                    }
                }
            }
        awaitClose { listenerRegistration.remove() }
    }

    suspend fun saveUser(user: UserModel, context: Context? = null) {
        if (context != null && !NetworkUtils.isNetworkAvailable(context)) {
            enqueueSync(context, "SAVE_USER", mapOf("user_json" to Gson().toJson(user)))
            return
        }
        firestore.collection("users").document(user.uid!!).set(user).await()
    }

    suspend fun updateUser(userModel: UserModel) {
        clearUserCache(userModel.uid!!)
        firestore.collection("users").document(userModel.uid!!).set(userModel).await()
    }

    suspend fun updateUserProfile(uid: String, updates: Map<String, Any>) {
        clearUserCache(uid)
        firestore.collection("users").document(uid).update(updates).await()
    }

    suspend fun updateUserStatus(uid: String, isOnline: Boolean, context: Context? = null) {
        clearUserCache(uid)
        if (context != null && !NetworkUtils.isNetworkAvailable(context)) {
            enqueueSync(context, "UPDATE_USER_STATUS", mapOf("uid" to uid, "is_online" to isOnline))
            return
        }
        firestore.collection("users").document(uid).update("isOnline", isOnline).await()
    }

    fun updateFcmToken(token: String) {
        val user = firebaseAuth.currentUser
        if (user != null) {
            firestore.collection("users").document(user.uid)
                .update("fcmToken", token)
                .addOnFailureListener { /* Log error */ }
        }
    }

    suspend fun blockUser(currentUserId: String, targetUserId: String) {
        firestore.collection("users").document(currentUserId)
            .update("blockedUsers", FieldValue.arrayUnion(targetUserId)).await()
    }

    fun clearUserCache(uid: String) {
        cache.remove("user_$uid")
    }

    fun updateNotificationPreferences(preferences: Map<String, Any>, callback: (Boolean, String?) -> Unit) {
        val user = firebaseAuth.currentUser
        if (user != null) {
            firestore.collection("users").document(user.uid)
                .update(preferences)
                .addOnSuccessListener { callback(true, null) }
                .addOnFailureListener { e -> callback(false, e.message) }
        } else {
            callback(false, "User not logged in")
        }
    }

    /**
     * Archives a user by moving their data to 'archived_users' collection and deleting from 'users'.
     */
    suspend fun archiveUser(uid: String) {
        firestore.runTransaction { transaction ->
            val userRef = firestore.collection("users").document(uid)
            val archiveRef = firestore.collection("archived_users").document(uid)

            val snapshot = transaction.get(userRef)
            if (snapshot.exists()) {
                val userData = snapshot.data
                if (userData != null) {
                    val archivalData = HashMap(userData)
                    archivalData["isArchived"] = true
                    archivalData["archivedAt"] = System.currentTimeMillis()
                    transaction.set(archiveRef, archivalData)
                    transaction.delete(userRef)
                }
            }
            null
        }.await()
    }

    // --- CA List ---

    suspend fun getCAList(): List<UserModel> {
        val cacheKey = "ca_list_page0"
        if (isCacheValid(cacheKey, 60_000L)) {
            @Suppress("UNCHECKED_CAST")
            val cached = cache.get(cacheKey)?.data as? List<UserModel>
            if (cached != null) return cached
        }

        val result = getCaPage(50, null)
        cache.put(cacheKey, CacheEntry(result.items, System.currentTimeMillis()))
        return result.items
    }

    data class PageResult<T>(val items: List<T>, val lastSnapshot: com.google.firebase.firestore.DocumentSnapshot?)

    suspend fun getCaPage(
        limit: Int,
        lastSnapshot: com.google.firebase.firestore.DocumentSnapshot?
    ): PageResult<UserModel> {
        try {
            var query: com.google.firebase.firestore.Query = firestore.collection("users")
                .whereEqualTo("role", "CA")
                .orderBy("rating", Query.Direction.DESCENDING)
                .limit(limit.toLong())
            if (lastSnapshot != null) {
                query = query.startAfter(lastSnapshot)
            }
            val snapshot = query.get().await()
            val caList = snapshot.documents.mapNotNull { it.toObject(UserModel::class.java) }
            val lastDoc = snapshot.documents.lastOrNull()
            return PageResult(caList, lastDoc)
        } catch (e: Exception) {
            // Fallback if index not ready
            var fallback: com.google.firebase.firestore.Query = firestore.collection("users")
                .whereEqualTo("role", "CA")
                .limit(limit.toLong())
            if (lastSnapshot != null) {
                fallback = fallback.startAfter(lastSnapshot)
            }
            val snapshot = fallback.get().await()
            val caList = snapshot.documents
                .mapNotNull { it.toObject(UserModel::class.java) }
                .sortedByDescending { it.rating }
            val lastDoc = snapshot.documents.lastOrNull()
            return PageResult(caList, lastDoc)
        }
    }

    // --- Sync ---

    private fun enqueueSync(context: Context, type: String, data: Map<String, Any?>) {
        val inputData = Data.Builder().putString("sync_type", type)
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
}
