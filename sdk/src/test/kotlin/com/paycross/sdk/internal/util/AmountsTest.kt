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
    fun `major string is a plain decimal with no grouping`() {
        assertEquals("123.45", Amounts.toMajorString(12345, "EUR"))
        assertEquals("1234567.89", Amounts.toMajorString(123456789, "USD"))
        assertEquals("0.05", Amounts.toMajorString(5, "EUR"))
        assertEquals("151.00", Amounts.toMajorString(15100, "EUR"))
    }

    @Test
    fun `major string keeps zero-decimal currencies undivided`() {
        assertEquals("5000", Amounts.toMajorString(5000, "JPY"))
        assertEquals("15100", Amounts.toMajorString(15100, "krw"))
    }

    @Test
    fun `fraction digits`() {
        assertEquals(2, Amounts.fractionDigits("EUR"))
        assertEquals(0, Amounts.fractionDigits("jpy"))
    }
}
