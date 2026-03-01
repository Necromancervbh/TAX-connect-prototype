package com.example.taxconnect.data.repositories

import android.content.Context
import com.example.taxconnect.core.network.NetworkUtils
import com.example.taxconnect.data.models.ServiceModel
import com.example.taxconnect.data.services.FirestoreSyncWorker
import androidx.work.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceRepository @Inject constructor() {

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    suspend fun saveService(service: ServiceModel, context: Context? = null) {
        if (context != null && !NetworkUtils.isNetworkAvailable(context)) {
            enqueueSync(context, "SAVE_SERVICE", mapOf("service_json" to Gson().toJson(service)))
            return
        }
        firestore.collection("services").document(service.id!!).set(service).await()
    }

    suspend fun getServices(caId: String): List<ServiceModel> {
        val snapshot = firestore.collection("services")
            .whereEqualTo("caId", caId)
            .get().await()
        return snapshot.documents.mapNotNull { it.toObject(ServiceModel::class.java) }
    }

    suspend fun deleteService(serviceId: String, context: Context? = null) {
        if (context != null && !NetworkUtils.isNetworkAvailable(context)) {
            enqueueSync(context, "DELETE_SERVICE", mapOf("service_id" to serviceId))
            return
        }
        firestore.collection("services").document(serviceId).delete().await()
    }

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
