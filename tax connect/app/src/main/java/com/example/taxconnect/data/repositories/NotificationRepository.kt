package com.example.taxconnect.data.repositories

import com.example.taxconnect.data.models.NotificationModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class NotificationRepository(private val context: android.content.Context? = null) {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val analytics = context?.let { AnalyticsRepository.getInstance(it) }

    fun saveNotification(notification: NotificationModel) {
        val userId = notification.userId ?: auth.uid ?: return
        firestore.collection("users").document(userId)
            .collection("notifications").add(notification)
            .addOnSuccessListener {
                logAnalytics("notification_saved", notification.type ?: "unknown")
            }
    }

    private fun logAnalytics(event: String, type: String) {
        analytics?.log(event, mapOf("notification_type" to type))
    }

    fun getNotifications(
        callback: (List<NotificationModel>) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        val userId = auth.uid ?: return
        firestore.collection("users").document(userId)
            .collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError?.invoke(e.message ?: "Failed to load notifications")
                    return@addSnapshotListener
                }
                val notifications = snapshot?.documents?.mapNotNull {
                    it.toObject(NotificationModel::class.java)?.copy(id = it.id)
                } ?: emptyList()
                callback(notifications)
            }
    }

    fun markAsRead(notificationId: String) {
        val userId = auth.uid ?: return
        firestore.collection("users").document(userId)
            .collection("notifications").document(notificationId)
            .update("read", true)
    }

    fun clearAll() {
        val userId = auth.uid ?: return
        firestore.collection("users").document(userId)
            .collection("notifications").get().addOnSuccessListener { snapshot ->
                val batch = firestore.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit()
            }
    }
}
