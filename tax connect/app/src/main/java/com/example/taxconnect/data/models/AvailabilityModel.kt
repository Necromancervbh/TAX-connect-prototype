package com.example.taxconnect.data.models

import java.io.Serializable

/**
 * Represents a CA's availability for a specific day of the week.
 * Stored in Firestore at: users/{caUid}/availability/{DAY_OF_WEEK}
 *
 * DAY_OF_WEEK values: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
 *
 * Generated slots are every [slotDurationMins] minutes from [startHour] to [endHour].
 * Example: startHour=9, endHour=18, slotDurationMins=60 → 9 AM, 10 AM, … 5 PM
 */
data class AvailabilityModel(
    var day: String? = null,            // e.g. "MONDAY"
    var enabled: Boolean = false,
    var startHour: Int = 9,             // 24-hour format (9 = 9 AM)
    var endHour: Int = 18,              // 24-hour format (18 = 6 PM, exclusive)
    var slotDurationMins: Int = 60,     // Slot spacing in minutes
    var breakStartHour: Int = 13,       // Lunch break start (24-hr) — slots skipped during break
    var breakEndHour: Int = 14          // Lunch break end (24-hr)
) : Serializable
