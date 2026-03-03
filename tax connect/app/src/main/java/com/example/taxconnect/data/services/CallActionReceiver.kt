package com.example.taxconnect.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.work.*
import com.example.taxconnect.features.videocall.VideoCallActivity
import com.example.taxconnect.data.repositories.AnalyticsRepository
import com.example.taxconnect.data.repositories.ConversationRepository
import com.example.taxconnect.data.repositories.ChatRepository
import com.example.taxconnect.data.models.MessageModel
import com.example.taxconnect.core.utils.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_ANSWER = "com.example.taxconnect.ACTION_ANSWER"
        const val ACTION_DECLINE = "com.example.taxconnect.ACTION_DECLINE"
        const val ACTION_REPLY = "com.example.taxconnect.ACTION_REPLY"
        const val ACTION_MARK_READ = "com.example.taxconnect.ACTION_MARK_READ"
        const val ACTION_ACCEPT_REQUEST = "com.example.taxconnect.ACTION_ACCEPT_REQUEST"
        const val ACTION_DECLINE_REQUEST = "com.example.taxconnect.ACTION_DECLINE_REQUEST"
        const val ACTION_ACCEPT_BOOKING = "com.example.taxconnect.ACTION_ACCEPT_BOOKING"
        const val ACTION_DECLINE_BOOKING = "com.example.taxconnect.ACTION_DECLINE_BOOKING"
        private const val TAG = "CallActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action == null) return

        val action = intent.action
        val notificationId = intent.getIntExtra("notification_id", intent.getIntExtra("notificationId", -1))
        val channelName = intent.getStringExtra("CHANNEL_NAME")
        val roomUuid = intent.getStringExtra("ROOM_UUID")
        val callerName = intent.getStringExtra("CALLER_NAME")
        val callerAvatar = intent.getStringExtra("CALLER_AVATAR")
        val chatId = intent.getStringExtra("chatId")
        val requestId = intent.getStringExtra("requestId")
        val bookingId = intent.getStringExtra("bookingId")

        // Log action click
        AnalyticsRepository.getInstance(context).log("notification_action_click", mapOf(
            "action" to (action ?: "unknown"),
            "chatId" to (chatId ?: "unknown")
        ))

        when (action) {
            ACTION_ANSWER         -> handleAnswer(context, notificationId, channelName, roomUuid, callerName, callerAvatar)
            ACTION_DECLINE        -> handleDecline(context, notificationId, channelName, roomUuid)
            ACTION_REPLY          -> handleReply(context, intent, notificationId, chatId)
            ACTION_MARK_READ      -> handleMarkRead(context, notificationId)
            ACTION_ACCEPT_REQUEST -> handleRequestAction(context, notificationId, requestId, "Accepted")
            ACTION_DECLINE_REQUEST-> handleRequestAction(context, notificationId, requestId, "Refused")
            ACTION_ACCEPT_BOOKING -> handleBookingAction(context, notificationId, bookingId, "CONFIRMED")
            ACTION_DECLINE_BOOKING-> handleBookingAction(context, notificationId, bookingId, "CANCELLED")
        }
    }

    private fun handleMarkRead(context: Context, notificationId: Int) {
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
        AnalyticsRepository.getInstance(context).log("notification_marked_read", emptyMap())
    }

    private fun handleRequestAction(context: Context, notificationId: Int, requestId: String?, status: String) {
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
        if (requestId == null) return

        val data = Data.Builder()
            .putString("type", "connection")
            .putString("id", requestId)
            .putString("status", status)
            .build()

        enqueueStatusWork(context, data)
        AnalyticsRepository.getInstance(context).log("request_action", mapOf("status" to status, "id" to requestId))
    }

    private fun handleBookingAction(context: Context, notificationId: Int, bookingId: String?, status: String) {
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
        if (bookingId == null) return

        if (status == "CANCELLED" || status == "REJECTED") {
            com.example.taxconnect.core.utils.BookingReminderScheduler.cancel(context, bookingId)
        }

        val data = Data.Builder()
            .putString("type", "booking")
            .putString("id", bookingId)
            .putString("status", status)
            .build()

        enqueueStatusWork(context, data)
        AnalyticsRepository.getInstance(context).log("booking_action", mapOf("status" to status, "id" to bookingId))
    }

    private fun enqueueStatusWork(context: Context, data: Data) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<RequestStatusWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    private fun handleAnswer(context: Context, notificationId: Int, channelName: String?, roomUuid: String?, callerName: String?, callerAvatar: String?) {
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
        if (VideoCallActivity.isInCall) {
            if (channelName != null) {
                ConversationRepository.getInstance().updateCallStatus(channelName, "BUSY", null)
            }
            AnalyticsRepository.getInstance(context).log("call_busy", mapOf("channelName" to (channelName ?: "")))
            return
        }
        if (channelName != null) {
            ConversationRepository.getInstance().updateCallStatus(channelName, "ACCEPTED", null)
        }
        val callIntent = Intent(context, VideoCallActivity::class.java).apply {
            putExtra("CHANNEL_NAME", channelName)
            putExtra("ROOM_UUID", roomUuid)
            putExtra("CALLER_NAME", callerName)
            putExtra("CALLER_AVATAR", callerAvatar)
            putExtra("INCOMING_CALL", true)
            putExtra("ACTION", "ANSWER")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(callIntent)
        AnalyticsRepository.getInstance(context).log("call_answered", mapOf("channelName" to (channelName ?: "")))
    }

    private fun handleDecline(context: Context, notificationId: Int, channelName: String?, roomUuid: String?) {
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
        if (channelName != null) {
            ConversationRepository.getInstance().updateCallStatus(channelName, "REJECTED", null)
        }
        AnalyticsRepository.getInstance(context).log("call_declined", mapOf("channelName" to (channelName ?: "")))
    }

    private fun handleReply(context: Context, intent: Intent, notificationId: Int, chatId: String?) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()
        val senderId = intent.getStringExtra("senderId")

        if (replyText != null && chatId != null) {
            val data = Data.Builder()
                .putString("chatId", chatId)
                .putString("receiverId", senderId)
                .putString("message", replyText)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val replyRequest = OneTimeWorkRequestBuilder<ReplyWorker>()
                .setInputData(data)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueue(replyRequest)

            // Update notification to show "Sending..."
            val sendingNotification = NotificationHelper.getBaseNotificationBuilder(
                context, NotificationHelper.CHANNEL_MESSAGES, NotificationHelper.GROUP_MESSAGES)
                .setContentText("Sending reply...")
                .setRemoteInputHistory(arrayOf(replyText))
                .build()

            // Check for POST_NOTIFICATIONS permission before notifying
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    NotificationManagerCompat.from(context).notify(notificationId, sendingNotification)
                } else {
                    Log.w(TAG, "POST_NOTIFICATIONS permission not granted, cannot show reply notification.")
                }
            } else {
                NotificationManagerCompat.from(context).notify(notificationId, sendingNotification)
            }
        }
    }
}

