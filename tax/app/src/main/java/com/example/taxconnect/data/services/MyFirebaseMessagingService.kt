package com.example.taxconnect.data.services

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat
import com.example.taxconnect.R
import com.example.taxconnect.data.repositories.ConversationRepository
import com.example.taxconnect.data.repositories.UserRepository
import com.example.taxconnect.core.utils.NotificationHelper
import com.example.taxconnect.core.utils.SecurityUtils
import com.example.taxconnect.features.home.MainActivity
import com.example.taxconnect.features.booking.RequestsActivity
import com.example.taxconnect.features.videocall.VideoCallActivity
import com.example.taxconnect.features.booking.MyBookingsActivity
import com.example.taxconnect.features.chat.ChatActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.Calendar
import com.example.taxconnect.data.repositories.AnalyticsRepository

import com.example.taxconnect.data.models.NotificationModel
import com.example.taxconnect.data.repositories.NotificationRepository
import com.google.firebase.auth.FirebaseAuth

class MyFirebaseMessagingService : FirebaseMessagingService() {
    
    private lateinit var notificationRepository: NotificationRepository

    override fun onCreate() {
        super.onCreate()
        notificationRepository = NotificationRepository(applicationContext)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        SecurityUtils.saveSecureString(applicationContext, "fcm_token", token)
        UserRepository.getInstance().updateFcmToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        // Log notification received
        val eventData = mutableMapOf<String, Any>()
        remoteMessage.messageId?.let { eventData["messageId"] = it }
        remoteMessage.data["type"]?.let { eventData["type"] = it }
        AnalyticsRepository.getInstance(applicationContext).log("notification_received", eventData)

        if (remoteMessage.data.isNotEmpty()) {
            handleDataMessage(remoteMessage.data)
        } else if (remoteMessage.notification != null) {
            // Notifications without data payload are just shown directly
            val notification = remoteMessage.notification!!
            val title = notification.title
            val body = notification.body
            
            // Save to history
            val currentUserId = FirebaseAuth.getInstance().uid
            if (currentUserId != null) {
                val model = NotificationModel(
                    userId = currentUserId,
                    type = "notification",
                    title = title,
                    body = body,
                    timestamp = System.currentTimeMillis(),
                    read = false
                )
                notificationRepository.saveNotification(model)
            }

            if (isQuietHours()) {
                AnalyticsRepository.getInstance(applicationContext).log("notification_suppressed_quiet_hours", eventData)
                return
            }
            
            sendNotification(
                title,
                body,
                null,
                NotificationHelper.CHANNEL_MESSAGES,
                NotificationHelper.GROUP_MESSAGES
            )
        }
    }

    private fun isQuietHours(): Boolean {
        val prefs = getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        val quietHoursEnabled = prefs.getBoolean("quiet_hours_enabled", false)
        if (!quietHoursEnabled) return false

        val startHour = prefs.getInt("quiet_hours_start_hour", 22)
        val startMin = prefs.getInt("quiet_hours_start_min", 0)
        val endHour = prefs.getInt("quiet_hours_end_hour", 7)
        val endMin = prefs.getInt("quiet_hours_end_min", 0)

        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)

        val nowInMins = hour * 60 + minute
        val startInMins = startHour * 60 + startMin
        val endInMins = endHour * 60 + endMin

        return if (startInMins <= endInMins) {
            nowInMins in startInMins..endInMins
        } else {
            nowInMins >= startInMins || nowInMins <= endInMins
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val type = data["type"]
        val title = data["title"]
        val body = data["body"]

        val prefs = getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        
        // Save to history
        val currentUserId = FirebaseAuth.getInstance().uid
        if (currentUserId != null) {
            val notification = NotificationModel(
                userId = currentUserId,
                type = type,
                title = title,
                body = body,
                data = data,
                timestamp = System.currentTimeMillis(),
                read = false
            )
            notificationRepository.saveNotification(notification)
        }

        // Check quiet hours after saving to ensure it appears in the app list
        if (isQuietHours()) {
             val eventData = mutableMapOf<String, Any>()
             data["messageId"]?.let { eventData["messageId"] = it }
             data["type"]?.let { eventData["type"] = it }
             AnalyticsRepository.getInstance(applicationContext).log("notification_suppressed_quiet_hours", eventData)
             return
        }

        if ("message" == type && !prefs.getBoolean("notify_messages", true)) return
        if ("call" == type && !prefs.getBoolean("notify_calls", true)) return
        if ("request" == type && !prefs.getBoolean("notify_requests", true)) return
        if ("booking" == type && !prefs.getBoolean("notify_bookings", true)) return

        when (type) {
            "message" -> {
                val senderId = data["senderId"]
                val senderName = data["senderName"]
                val chatId = data["chatId"]
                val pendingIntent = NotificationHelper.getDeepLinkPendingIntent(this, "message", data)
                sendMessageNotification(title, body, senderName, chatId, senderId, pendingIntent)
            }
            "call" -> {
                val channelName = data["channelName"]
                val roomUuid = data["roomUuid"]
                val callerName = data["callerName"]
                val callerAvatar = data["callerAvatar"]
                if (VideoCallActivity.isInCall) {
                    if (channelName != null) {
                        ConversationRepository.getInstance().updateCallStatus(channelName, "BUSY", null)
                    }
                    return
                }
                sendCallNotification(title, body, channelName, roomUuid, callerName, callerAvatar)
            }
            "request" -> {
                showRequestNotification(title, body, data)
            }
            "booking" -> {
                val pendingIntent = NotificationHelper.getDeepLinkPendingIntent(this, "booking", data)
                sendNotificationWithPendingIntent(title, body, pendingIntent, NotificationHelper.CHANNEL_BOOKINGS, NotificationHelper.GROUP_BOOKINGS, data)
            }
            else -> {
                val pendingIntent = NotificationHelper.getDeepLinkPendingIntent(this, "default", data)
                sendNotificationWithPendingIntent(title, body, pendingIntent, NotificationHelper.CHANNEL_MESSAGES, NotificationHelper.GROUP_MESSAGES, data)
            }
        }
    }

    private fun sendMessageNotification(title: String?, body: String?, senderName: String?, chatId: String?, senderId: String?, pendingIntent: PendingIntent) {
        val notificationId = chatId?.hashCode() ?: System.currentTimeMillis().toInt()
        
        val replyIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_REPLY
            putExtra("chatId", chatId)
            putExtra("notificationId", notificationId)
            putExtra("senderId", senderId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            this, notificationId + 100, replyIntent, flags
        )

        val remoteInput = androidx.core.app.RemoteInput.Builder(NotificationHelper.KEY_TEXT_REPLY)
            .setLabel(getString(R.string.notification_reply_hint))
            .build()

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_chat_bubble_material3, getString(R.string.notification_action_reply), replyPendingIntent)
            .addRemoteInput(remoteInput)
            .build()

        val user = androidx.core.app.Person.Builder()
            .setName("Me")
            .build()
        
        val sender = androidx.core.app.Person.Builder()
            .setName(senderName ?: "Sender")
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(user)
            .addMessage(body, System.currentTimeMillis(), sender)
            .setConversationTitle(senderName)

        val builder = NotificationHelper.getBaseNotificationBuilder(this, NotificationHelper.CHANNEL_MESSAGES, NotificationHelper.GROUP_MESSAGES)
            .setStyle(messagingStyle)
            .setContentIntent(pendingIntent)
            .addAction(replyAction)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShortcutId(chatId) // For conversation shortcuts (Android 11+)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
        
        NotificationHelper.showSummaryNotification(
            this, NotificationHelper.CHANNEL_MESSAGES, NotificationHelper.GROUP_MESSAGES,
            1001, "New Messages", "You have unread messages"
        )
    }

    private fun sendNotificationWithPendingIntent(title: String?, body: String?, pendingIntent: PendingIntent, channelId: String, groupKey: String, data: Map<String, String> = emptyMap()) {
        val notificationId = System.currentTimeMillis().toInt()

        if (channelId == NotificationHelper.CHANNEL_BOOKINGS) {
            val bookingId = data["bookingId"] ?: ""
            val isRequest = data["isRequest"] == "true"
            
            val remoteViews = android.widget.RemoteViews(packageName, R.layout.layout_notification_booking)
            remoteViews.setTextViewText(R.id.notification_title, title ?: "Booking Update")
            remoteViews.setTextViewText(R.id.notification_body, body ?: "Check your bookings")
            remoteViews.setOnClickPendingIntent(R.id.btn_view_details, pendingIntent)

            if (isRequest && bookingId.isNotEmpty()) {
                remoteViews.setViewVisibility(R.id.btn_accept, android.view.View.VISIBLE)
                remoteViews.setViewVisibility(R.id.btn_decline, android.view.View.VISIBLE)
                
                val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
                    action = CallActionReceiver.ACTION_ACCEPT_BOOKING
                    putExtra("bookingId", bookingId)
                    putExtra("notificationId", notificationId)
                }
                val acceptPendingIntent = PendingIntent.getBroadcast(this, notificationId + 3, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                remoteViews.setOnClickPendingIntent(R.id.btn_accept, acceptPendingIntent)

                val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
                    action = CallActionReceiver.ACTION_DECLINE_BOOKING
                    putExtra("bookingId", bookingId)
                    putExtra("notificationId", notificationId)
                }
                val declinePendingIntent = PendingIntent.getBroadcast(this, notificationId + 4, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                remoteViews.setOnClickPendingIntent(R.id.btn_decline, declinePendingIntent)
            }

            val builder = NotificationHelper.getBaseNotificationBuilder(this, channelId, groupKey)
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            NotificationHelper.createNotificationChannels(this)
            notificationManager.notify(notificationId, builder.build())
            return
        }

        val builder = NotificationHelper.getBaseNotificationBuilder(this, channelId, groupKey)
            .setContentTitle(title ?: "TaxConnect")
            .setContentText(body ?: "New Notification")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    private fun sendCallNotification(
        title: String?,
        body: String?,
        channelName: String?,
        roomUuid: String?,
        callerName: String?,
        callerAvatar: String?
    ) {
        val notificationId = System.currentTimeMillis().toInt()

        val answerIntent = Intent(this, VideoCallActivity::class.java).apply {
            putExtra("CHANNEL_NAME", channelName)
            putExtra("ROOM_UUID", roomUuid)
            putExtra("CALLER_NAME", callerName)
            putExtra("CALLER_AVATAR", callerAvatar)
            putExtra("INCOMING_CALL", true)
            putExtra("ACTION", "ANSWER")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val answerPendingIntent = PendingIntent.getActivity(
            this, notificationId + 1, answerIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE
            putExtra("notification_id", notificationId)
            putExtra("CHANNEL_NAME", channelName)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(
            this, notificationId + 2, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val fullScreenIntent = Intent(this, VideoCallActivity::class.java).apply {
            putExtra("CHANNEL_NAME", channelName)
            putExtra("ROOM_UUID", roomUuid)
            putExtra("CALLER_NAME", callerName)
            putExtra("INCOMING_CALL", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, notificationId + 3, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationHelper.getBaseNotificationBuilder(this, NotificationHelper.CHANNEL_CALLS, NotificationHelper.GROUP_CALLS)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setContentIntent(fullScreenPendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val caller = androidx.core.app.Person.Builder()
                .setName(callerName ?: "Incoming Call")
                .build()
            
            builder.setStyle(
                NotificationCompat.CallStyle.forIncomingCall(
                    caller, declinePendingIntent, answerPendingIntent
                )
            )
        } else {
            builder.setContentTitle(callerName ?: (title ?: "Incoming Call"))
                .setContentText(body ?: "Tap to answer")
                .addAction(R.drawable.ic_video_call_material3, "Answer", answerPendingIntent)
                .addAction(R.drawable.ic_close, "Decline", declinePendingIntent)
        }


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                val notificationManager = NotificationManagerCompat.from(this)
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                notificationManager.notify(notificationId, builder.build())
            }
        } else {
             NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        }
    }

    private fun sendNotification(title: String?, body: String?, intent: Intent?, channelId: String, groupKey: String) {
        val notificationId = System.currentTimeMillis().toInt()
        val finalIntent = intent ?: Intent(this, MainActivity::class.java)
        finalIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        val pendingIntent = PendingIntent.getActivity(
            this, notificationId, finalIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationHelper.getBaseNotificationBuilder(this, channelId, groupKey)
            .setContentTitle(title ?: "TaxConnect")
            .setContentText(body ?: "New Notification")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    private fun showRequestNotification(title: String?, body: String?, data: Map<String, String>) {
        val notificationId = System.currentTimeMillis().toInt()
        val senderId = data["senderId"] ?: ""
        val requestId = data["requestId"] ?: ""
        
        val acceptIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_ACCEPT_REQUEST
            putExtra("requestId", requestId)
            putExtra("senderId", senderId)
            putExtra("notificationId", notificationId)
        }
        val acceptPendingIntent = PendingIntent.getBroadcast(this, notificationId + 1, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val declineIntent = Intent(this, CallActionReceiver::class.java).apply {
            action = CallActionReceiver.ACTION_DECLINE_REQUEST
            putExtra("requestId", requestId)
            putExtra("senderId", senderId)
            putExtra("notificationId", notificationId)
        }
        val declinePendingIntent = PendingIntent.getBroadcast(this, notificationId + 2, declineIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val pendingIntent = NotificationHelper.getDeepLinkPendingIntent(this, "request", data)

        val builder = NotificationHelper.getBaseNotificationBuilder(this, NotificationHelper.CHANNEL_REQUESTS, NotificationHelper.GROUP_REQUESTS)
            .setContentTitle(title ?: "New Connection Request")
            .setContentText(body ?: "Someone wants to connect with you.")
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_check_material3, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_close, "Decline", declinePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        NotificationHelper.createNotificationChannels(this)
        notificationManager.notify(notificationId, builder.build())
    }
}
