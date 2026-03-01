package com.example.taxconnect.data.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class RequestStatusWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val type = inputData.getString("type") ?: return Result.failure() // "connection" or "booking"
        val id = inputData.getString("id") ?: return Result.failure()
        val status = inputData.getString("status") ?: return Result.failure()
        
        return try {
            val db = FirebaseFirestore.getInstance()
            
            when (type) {
                "connection" -> {
                    db.collection("conversations").document(id)
                        .update("workflowState", status)
                        .await()
                }
                "booking" -> {
                    db.collection("bookings").document(id)
                        .update("status", status)
                        .await()
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
