package com.example.taxconnect.core.utils

import java.util.Calendar

data class TaxDeadline(
    val title: String,
    val description: String,
    val month: Int,   // Calendar.MONTH (0-based)
    val day: Int,
    val year: Int,
    val colorHex: String = "#3B82F6" // default blue
)

object TaxDeadlineHelper {

    // FY 2024-25 / AY 2025-26 deadlines
    private val DEADLINES = listOf(
        TaxDeadline(
            title = "Advance Tax (Q4 FY25)",
            description = "4th instalment of Advance Tax",
            month = Calendar.MARCH, day = 15, year = 2025,
            colorHex = "#F59E0B"
        ),
        TaxDeadline(
            title = "GSTR-3B (Feb)",
            description = "Monthly GST Return for February",
            month = Calendar.MARCH, day = 20, year = 2025,
            colorHex = "#8B5CF6"
        ),
        TaxDeadline(
            title = "TDS Payment (Feb)",
            description = "TDS / TCS deposit for February",
            month = Calendar.MARCH, day = 7, year = 2025,
            colorHex = "#EF4444"
        ),
        TaxDeadline(
            title = "ITR Filing (Non-Audit)",
            description = "Last date to file ITR for individuals",
            month = Calendar.JULY, day = 31, year = 2025,
            colorHex = "#10B981"
        ),
        TaxDeadline(
            title = "Advance Tax (Q1 FY26)",
            description = "1st instalment of Advance Tax",
            month = Calendar.JUNE, day = 15, year = 2025,
            colorHex = "#F59E0B"
        ),
        TaxDeadline(
            title = "GSTR-9 (Annual Return)",
            description = "Annual GST Return for FY 2024-25",
            month = Calendar.DECEMBER, day = 31, year = 2025,
            colorHex = "#8B5CF6"
        ),
        TaxDeadline(
            title = "ITR Filing (Audit Cases)",
            description = "Last date for businesses requiring audit",
            month = Calendar.OCTOBER, day = 31, year = 2025,
            colorHex = "#3B82F6"
        ),
        TaxDeadline(
            title = "Advance Tax (Q2 FY26)",
            description = "2nd instalment of Advance Tax",
            month = Calendar.SEPTEMBER, day = 15, year = 2025,
            colorHex = "#F59E0B"
        ),
        TaxDeadline(
            title = "Advance Tax (Q3 FY26)",
            description = "3rd instalment of Advance Tax",
            month = Calendar.DECEMBER, day = 15, year = 2025,
            colorHex = "#F59E0B"
        )
    )

    /**
     * Returns the next [count] upcoming deadlines (sorted by date), filtered to >= today.
     */
    fun getUpcomingDeadlines(count: Int = 3): List<TaxDeadline> {
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return DEADLINES
            .filter { deadline ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, deadline.year)
                    set(Calendar.MONTH, deadline.month)
                    set(Calendar.DAY_OF_MONTH, deadline.day)
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                }
                !cal.before(today)
            }
            .sortedWith(compareBy(
                { it.year },
                { it.month },
                { it.day }
            ))
            .take(count)
    }

    /** Returns epoch ms for a [TaxDeadline]'s date (end of that day). */
    fun deadlineEpoch(d: TaxDeadline): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, d.year)
            set(Calendar.MONTH, d.month)
            set(Calendar.DAY_OF_MONTH, d.day)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
        }.timeInMillis
    }

    /** Returns days remaining until deadline (0 = today, negative = past). */
    fun daysUntil(d: TaxDeadline): Int {
        val now = System.currentTimeMillis()
        val epoch = deadlineEpoch(d)
        return ((epoch - now) / (1000L * 60 * 60 * 24)).toInt()
    }

    /** Human-readable "Mar 15, 2025" format */
    fun formatDate(d: TaxDeadline): String {
        val months = arrayOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
        return "${months[d.month]} ${d.day}, ${d.year}"
    }
}
