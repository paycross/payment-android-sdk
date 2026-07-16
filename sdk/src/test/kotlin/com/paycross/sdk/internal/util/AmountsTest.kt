package com.paycross.sdk.internal.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AmountsTest {

    @Test
    fun `two-decimal currencies divide minor units by 100`() {
        assertEquals("€151.00", Amounts.formatMinor(15100, "EUR", Locale.US).replace(" ", " ").trim())
    }

    @Test
    fun `zero-decimal currencies pass minor units through`() {
        val formatted = Amounts.formatMinor(15100, "JPY", Locale.US)
        assertEquals("¥15,100", formatted)
    }

    @Test
    fun `fraction digits`() {
        assertEquals(2, Amounts.fractionDigits("EUR"))
        assertEquals(0, Amounts.fractionDigits("jpy"))
    }
}
