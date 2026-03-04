package com.example.taxconnect.core.utils

import com.example.taxconnect.data.models.AvailabilityModel
import java.util.Calendar
import java.util.Locale

/**
 * Utility that converts an AvailabilityModel into a list of time slot strings
 * for a given date, skipping past slots if the date is today.
 */
object SlotGenerator {

    private val DAYS = listOf("SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY",
        "THURSDAY", "FRIDAY", "SATURDAY")

    /** Returns the key (e.g. "MONDAY") for the given Calendar day */
    fun dayKey(cal: Calendar): String {
        val idx = cal.get(Calendar.DAY_OF_WEEK) - 1
        return DAYS.getOrElse(idx) { "MONDAY" }
    }

    /**
     * Generates slots from the given availability, filtered for [date].
     * Slots before [now] are excluded when [date] is today.
     */
    fun generate(availability: AvailabilityModel, date: Calendar, now: Calendar): List<String> {
        if (!availability.enabled) return emptyList()

        val isToday = isSameDay(date, now)
        val slots = mutableListOf<String>()

        var h = availability.startHour
        var m = 0

        while (h < availability.endHour) {
            // Skip break window
            val isBreak = h >= availability.breakStartHour && h < availability.breakEndHour
            if (!isBreak) {
                // Check if past for today
                val slotCal = date.clone() as Calendar
                slotCal.set(Calendar.HOUR_OF_DAY, h)
                slotCal.set(Calendar.MINUTE, m)
                slotCal.set(Calendar.SECOND, 0)

                val isPast = isToday && slotCal.before(now)
                if (!isPast) {
                    slots.add(formatSlot(h, m))
                }
            }

            // Advance by slotDurationMins
            m += availability.slotDurationMins
            h += m / 60
            m %= 60
        }

        return slots
    }

    /**
     * Generates slots from hardcoded defaults when no Firestore availability is configured.
     */
    fun generateDefault(date: Calendar, now: Calendar): Pair<List<String>, List<String>> {
        val isToday = isSameDay(date, now)

        fun filter(raw: List<String>): List<String> {
            if (!isToday) return raw
            return raw.filter { slot ->
                val slotCal = parseSlotTime(slot, date) ?: return@filter false
                slotCal.after(now)
            }
        }

        val morning = filter(listOf("09:00 AM", "10:00 AM", "11:00 AM", "12:00 PM"))
        val afternoon = filter(listOf("02:00 PM", "03:00 PM", "04:00 PM", "05:00 PM", "06:00 PM"))
        return Pair(morning, afternoon)
    }

    private fun formatSlot(hour: Int, minute: Int): String {
        val ampm = if (hour < 12) "AM" else "PM"
        val h = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return String.format(Locale.ENGLISH, "%02d:%02d %s", h, minute, ampm)
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun parseSlotTime(slot: String, base: Calendar): Calendar? {
        return try {
            val fmt = java.text.SimpleDateFormat("hh:mm a", Locale.ENGLISH)
            val parsed = fmt.parse(slot) ?: return null
            val slotCal = base.clone() as Calendar
            val tmp = Calendar.getInstance().apply { time = parsed }
            slotCal.set(Calendar.HOUR_OF_DAY, tmp.get(Calendar.HOUR_OF_DAY))
            slotCal.set(Calendar.MINUTE, tmp.get(Calendar.MINUTE))
            slotCal.set(Calendar.SECOND, 0)
            slotCal
        } catch (e: Exception) {
            null
        }
    }
}
