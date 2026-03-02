package com.example.taxconnect.core.utils

import java.util.regex.Pattern

object PaymentUtils {
    data class SplitResult(val advanceAmount: Double, val finalAmount: Double)

    private val UPI_PATTERN = Pattern.compile("^[a-zA-Z0-9.\\-_]{2,256}@[a-zA-Z]{2,64}$")

    fun splitAmount(totalAmount: Double, ratio: Double = 0.3): SplitResult {
        val effectiveRatio = if (ratio <= 0.0 || ratio >= 1.0) 0.3 else ratio
        val advance = totalAmount * effectiveRatio
        val final = totalAmount - advance
        return SplitResult(advance, final)
    }

    fun formatAmount(amount: Double): String {
        return if (amount % 1 == 0.0) {
            String.format("%.0f", amount)
        } else {
            String.format("%.2f", amount)
        }
    }

    fun parseAmount(amountStr: String): Double {
        return try {
            val cleanStr = amountStr.replace("[^0-9.]".toRegex(), "")
            cleanStr.toDouble()
        } catch (e: Exception) {
            0.0
        }
    }

    fun isValidUpiId(upiId: String): Boolean {
        return UPI_PATTERN.matcher(upiId).matches()
    }
}
