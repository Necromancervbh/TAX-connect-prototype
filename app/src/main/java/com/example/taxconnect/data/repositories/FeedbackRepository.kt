package com.example.taxconnect.data.repositories

import android.content.Context
import com.example.taxconnect.core.network.NetworkUtils
import com.example.taxconnect.data.models.FeedbackModel
import com.example.taxconnect.data.services.FirestoreSyncWorker
import androidx.work.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun saveFeedback(feedback: FeedbackModel, context: Context? = null) {
        if (context != null && !NetworkUtils.isNetworkAvailable(context)) {
            enqueueSync(context, mapOf("feedback_json" to Gson().toJson(feedback)))
            return
        }
        val id = firestore.collection("feedback").document().id
        firestore.collection("feedback").document(id).set(feedback).await()
    }

    private fun enqueueSync(context: Context, data: Map<String, Any?>) {
        val inputData = Data.Builder().putString("sync_type", "SAVE_FEEDBACK")
        data.forEach { (key, value) ->
            if (value is String) inputData.putString(key, value)
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
