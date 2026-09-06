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
    fun `French puts the symbol last and the comma in the middle`() {
        // The separator CLDR picks here is a no-break space of one width or
        // another, and which one has changed between ICU releases. The point of
        // the assertion is the comma and the trailing symbol.
        assertEquals("12,34 EUR".replace("EUR", "\u20AC"), spaces(Amounts.formatMinor(1234, "EUR", Locale.FRENCH)))
        assertEquals("1 234,00 EUR".replace("EUR", "\u20AC"), spaces(Amounts.formatMinor(123400, "EUR", Locale.FRENCH)))
    }

    @Test
    fun `French keeps a zero-decimal currency undivided`() {
        assertEquals("15 100 JPY", spaces(Amounts.formatMinor(15100, "JPY", Locale.FRENCH)))
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

    // Every no-break space CLDR might use, flattened to a plain one.
    private fun spaces(formatted: String): String =
        formatted.replace('\u00A0', ' ').replace('\u202F', ' ')

    @Test
    fun `fraction digits`() {
        assertEquals(2, Amounts.fractionDigits("EUR"))
        assertEquals(0, Amounts.fractionDigits("jpy"))
    }
}
