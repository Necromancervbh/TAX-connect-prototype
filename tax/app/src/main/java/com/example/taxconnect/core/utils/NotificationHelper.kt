package com.example.taxconnect.core.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.example.taxconnect.R
import com.example.taxconnect.features.home.MainActivity

object NotificationHelper {

    const val CHANNEL_MESSAGES = "channel_messages"
    const val CHANNEL_CALLS = "channel_calls"
    const val CHANNEL_REQUESTS = "channel_requests"
    const val CHANNEL_BOOKINGS = "channel_bookings"

    const val GROUP_MESSAGES = "com.example.taxconnect.MESSAGES"
    const val GROUP_CALLS = "com.example.taxconnect.CALLS"
    const val GROUP_REQUESTS = "com.example.taxconnect.REQUESTS"
    const val GROUP_BOOKINGS = "com.example.taxconnect.BOOKINGS"

    const val KEY_TEXT_REPLY = "key_text_reply"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        if (!isIgnoringBatteryOptimizations(context)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Check if channels already exist to avoid re-creating them unnecessarily
            if (notificationManager.getNotificationChannel(CHANNEL_MESSAGES) != null &&
                notificationManager.getNotificationChannel(CHANNEL_CALLS) != null) {
                return
            }

            val channels = mutableListOf<NotificationChannel>()

            // Messages Channel
            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming messages"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(true)
                setShowBadge(true)
            }
            channels.add(messagesChannel)

            // Calls Channel
            val callsChannel = NotificationChannel(
                CHANNEL_CALLS,
                "Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming video and voice calls"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build()
                )
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            channels.add(callsChannel)

            // Requests Channel
            val requestsChannel = NotificationChannel(
                CHANNEL_REQUESTS,
                "Connection Requests",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for new connection requests"
                enableLights(true)
                lightColor = Color.GREEN
                setShowBadge(true)
                enableVibration(true)
            }
            channels.add(requestsChannel)

            // Bookings Channel
            val bookingsChannel = NotificationChannel(
                CHANNEL_BOOKINGS,
                "Bookings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for booking confirmations and cancellations"
                enableLights(true)
                lightColor = Color.YELLOW
                setShowBadge(true)
                enableVibration(true)
            }
            channels.add(bookingsChannel)

            notificationManager.createNotificationChannels(channels)
        }
    }

    fun getBaseNotificationBuilder(context: Context, channelId: String, groupKey: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notifications_material3)
            .setAutoCancel(true)
            .setGroup(groupKey)
            .setColor(context.getColor(R.color.primary))
    }

    fun getDeepLinkPendingIntent(context: Context, type: String, data: Map<String, String>): PendingIntent {
        val intent = when (type) {
            "message" -> {
                Intent(context, com.example.taxconnect.features.chat.ChatActivity::class.java).apply {
                    putExtra("chatId", data["chatId"])
                    putExtra("otherUserId", data["senderId"])
                    putExtra("otherUserName", data["senderName"])
                }
            }
            "call" -> {
                Intent(context, com.example.taxconnect.features.chat.ChatActivity::class.java).apply {
                    putExtra("chatId", data["chatId"])
                    putExtra("otherUserId", data["senderId"])
                    putExtra("otherUserName", data["senderName"])
                }
            }
            "request" -> {
                Intent(context, com.example.taxconnect.features.ca.CADetailActivity::class.java).apply {
                    putExtra("chatId", data["chatId"])
                    putExtra("userId", data["senderId"])
                }
            }
            "booking" -> {
                Intent(context, com.example.taxconnect.features.booking.MyBookingsActivity::class.java)
            }
            else -> Intent(context, com.example.taxconnect.features.home.MainActivity::class.java)
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        return PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun showSummaryNotification(context: Context, channelId: String, groupKey: String, notificationId: Int, title: String, content: String) {
        val summaryNotification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notifications_material3)
            .setStyle(NotificationCompat.InboxStyle()
                .setSummaryText(content))
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(context).notify(notificationId, summaryNotification)
            } else {
                // Log a warning or handle the case where permission is not granted
                // For now, we'll just skip showing the notification
            }
        } else {
            NotificationManagerCompat.from(context).notify(notificationId, summaryNotification)
        }
    }
}
