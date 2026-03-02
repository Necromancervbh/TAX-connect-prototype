package com.example.taxconnect.core.utils

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.taxconnect.core.workers.BookingReminderWorker
import com.example.taxconnect.data.models.BookingModel
import java.util.concurrent.TimeUnit

object BookingReminderScheduler {

    private const val REMINDER_MINUTES_BEFORE = 30L

    /**
     * Schedule a local reminder notification 30 minutes before [booking]'s appointment.
     * If the appointment is already < 30 minutes away, nothing is scheduled.
     * The work is tagged with "reminder_<bookingId>" for easy cancellation.
     */
    fun schedule(context: Context, booking: BookingModel, otherPartyName: String) {
        val bookingId = booking.id ?: return
        val now = System.currentTimeMillis()
        val reminderAt = booking.appointmentTimestamp - TimeUnit.MINUTES.toMillis(REMINDER_MINUTES_BEFORE)
        val delay = reminderAt - now

        if (delay <= 0) return // Already past trigger window

        val inputData = Data.Builder()
            .putString(BookingReminderWorker.KEY_BOOKING_ID, bookingId)
            .putString(BookingReminderWorker.KEY_OTHER_PARTY_NAME, otherPartyName)
            .putString(BookingReminderWorker.KEY_APPOINTMENT_TIME_LABEL, "$REMINDER_MINUTES_BEFORE minutes")
            .build()

        val request = OneTimeWorkRequestBuilder<BookingReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(inputData)
            .addTag("reminder_$bookingId")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "reminder_$bookingId",
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Cancel a previously scheduled reminder (e.g., on booking decline/cancellation). */
    fun cancel(context: Context, bookingId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("reminder_$bookingId")
    }
}
