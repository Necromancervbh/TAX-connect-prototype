package com.example.taxconnect.core.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentUtilsTest {
    @Test
    fun splitAmount_usesDefaultRatioWhenInvalid() {
        val result = PaymentUtils.splitAmount(1000.0, -1.0)
        assertEquals(300.0, result.advanceAmount, 0.001)
        assertEquals(700.0, result.finalAmount, 0.001)
    }

    @Test
    fun formatAmount_formatsIntegersWithoutDecimals() {
        assertEquals("100", PaymentUtils.formatAmount(100.0))
    }

    @Test
    fun formatAmount_formatsDecimalsWithTwoPlaces() {
        assertEquals("100.50", PaymentUtils.formatAmount(100.5))
    }

    @Test
    fun parseAmount_stripsCurrencySymbols() {
        assertEquals(1234.5, PaymentUtils.parseAmount("₹ 1,234.5"), 0.001)
    }
}
