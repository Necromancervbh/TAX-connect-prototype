package com.example.taxconnect.data.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.taxconnect.data.models.FeedbackModel
import com.example.taxconnect.data.models.ServiceModel
import com.example.taxconnect.data.models.UserModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import timber.log.Timber

class FirestoreSyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val type = inputData.getString("sync_type") ?: return Result.failure()
        val db = FirebaseFirestore.getInstance()
        val gson = Gson()

        return try {
            when (type) {
                "SAVE_USER" -> {
                    val userJson = inputData.getString("user_json") ?: return Result.failure()
                    val user = gson.fromJson(userJson, UserModel::class.java)
                    db.collection("users").document(user.uid!!).set(user).await()
                }
                "SAVE_SERVICE" -> {
                    val serviceJson = inputData.getString("service_json") ?: return Result.failure()
                    val service = gson.fromJson(serviceJson, ServiceModel::class.java)
                    db.collection("services").document(service.id!!).set(service).await()
                }
                "DELETE_SERVICE" -> {
                    val serviceId = inputData.getString("service_id") ?: return Result.failure()
                    db.collection("services").document(serviceId).delete().await()
                }
                "UPDATE_USER_STATUS" -> {
                    val uid = inputData.getString("uid") ?: return Result.failure()
                    val isOnline = inputData.getBoolean("is_online", false)
                    db.collection("users").document(uid).update("online", isOnline).await()
                }
                "SAVE_FEEDBACK" -> {
                    val feedbackJson = inputData.getString("feedback_json") ?: return Result.failure()
                    val feedback = gson.fromJson(feedbackJson, FeedbackModel::class.java)
                    val id = db.collection("feedback").document().id
                    db.collection("feedback").document(id).set(feedback).await()
                }
            }
            Timber.d("Sync successful for $type")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Sync failed for $type")
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
