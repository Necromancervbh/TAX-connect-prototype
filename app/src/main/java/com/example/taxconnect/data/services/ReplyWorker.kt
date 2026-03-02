package com.example.taxconnect.data.services

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class ReplyWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val receiverId = inputData.getString("receiverId") ?: ""
        val chatId = inputData.getString("chatId")
        val messageText = inputData.getString("message") ?: return Result.failure()
        val senderId = FirebaseAuth.getInstance().uid ?: return Result.failure()

        return try {
            val db = FirebaseFirestore.getInstance()
            val finalChatId = chatId ?: if (senderId < receiverId) "${senderId}_$receiverId" else "${receiverId}_$senderId"
            
            val message = hashMapOf(
                "senderId" to senderId,
                "receiverId" to receiverId,
                "message" to messageText,
                "timestamp" to System.currentTimeMillis(),
                "type" to "text"
            )

            db.collection("conversations").document(finalChatId)
                .collection("messages")
                .add(message)
                .await()

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
