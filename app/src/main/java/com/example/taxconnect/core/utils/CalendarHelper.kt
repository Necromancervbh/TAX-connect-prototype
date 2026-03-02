package com.example.taxconnect.core.utils

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.example.taxconnect.data.models.BookingModel
import java.util.*

object CalendarHelper {
    
    /**
     * Check if calendar permissions are granted
     */
    fun hasCalendarPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Add booking to device calendar
     * @return Event ID if successful, null otherwise
     */
    fun addBookingToCalendar(
        context: Context,
        booking: BookingModel
    ): Long? {
        if (!hasCalendarPermission(context)) {
            return null
        }
        
        try {
            val calendarId = getDefaultCalendarId(context) ?: return null
            
            val values = ContentValues().apply {
                put(CalendarContract.Events.DTSTART, booking.appointmentTimestamp)
                put(CalendarContract.Events.DTEND, booking.appointmentTimestamp + (60 * 60 * 1000)) // 1 hour duration
                put(CalendarContract.Events.TITLE, "Tax Consultation with ${booking.caName ?: "CA"}")
                put(CalendarContract.Events.DESCRIPTION, booking.message ?: "Tax consultation appointment")
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1) // Enable reminders
            }
            
            val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            val eventId = uri?.lastPathSegment?.toLongOrNull()
            
            // Add reminder 1 hour before
            if (eventId != null) {
                addReminder(context, eventId, 60) // 60 minutes before
            }
            
            return eventId
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    /**
     * Get the default calendar ID
     */
    private fun getDefaultCalendarId(context: Context): Long? {
        if (!hasCalendarPermission(context)) {
            return null
        }
        
        try {
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.IS_PRIMARY
            )
            
            val cursor = context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val isPrimary = it.getInt(1)
                    if (isPrimary == 1) {
                        return id
                    }
                }
                // If no primary calendar, return the first one
                if (it.moveToFirst()) {
                    return it.getLong(0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return null
    }
    
    /**
     * Add reminder to calendar event
     */
    private fun addReminder(context: Context, eventId: Long, minutesBefore: Int) {
        try {
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutesBefore)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
