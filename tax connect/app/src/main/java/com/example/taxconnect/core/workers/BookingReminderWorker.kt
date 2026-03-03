package com.example.taxconnect.core.workers

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.taxconnect.R
import com.example.taxconnect.core.utils.NotificationHelper

class BookingReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_BOOKING_ID = "booking_id"
        const val KEY_OTHER_PARTY_NAME = "other_party_name"
        const val KEY_APPOINTMENT_TIME_LABEL = "appointment_time_label"
        const val NOTIFICATION_TAG = "booking_reminder"
    }

    override suspend fun doWork(): Result {
        val bookingId = inputData.getString(KEY_BOOKING_ID) ?: return Result.failure()
        val otherPartyName = inputData.getString(KEY_OTHER_PARTY_NAME) ?: "your client"
        val timeLabel = inputData.getString(KEY_APPOINTMENT_TIME_LABEL) ?: "30 minutes"

        val notifId = (bookingId.hashCode() and 0x7FFFFFFF)

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_BOOKINGS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.reminder_notif_title))
            .setContentText(
                context.getString(R.string.reminder_notif_body, otherPartyName, timeLabel)
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    context.getString(R.string.reminder_notif_body, otherPartyName, timeLabel)
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setColorized(true)
            .setColor(context.getColor(R.color.primary))
            .build()

        try {
            androidx.core.app.NotificationManagerCompat.from(context)
                .notify(NOTIFICATION_TAG, notifId, notification)
        } catch (_: SecurityException) {
            // Notification permission not granted -- silently skip
        }

        return Result.success()
    }
}
