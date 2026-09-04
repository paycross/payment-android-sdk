package com.paycross.sdk.internal.validation

import org.junit.Assert.assertEquals
import org.junit.Test

class CardTypeTest {

    @Test
    fun `detects Visa`() {
        assertEquals(CardType.VISA, CardType.detect("4111111111111111"))
        assertEquals(CardType.VISA, CardType.detect("4532010000000366"))
    }

    @Test
    fun `detects Mastercard`() {
        assertEquals(CardType.MASTERCARD, CardType.detect("5500000000000004"))
        assertEquals(CardType.MASTERCARD, CardType.detect("2221000000000009"))
    }

    @Test
    fun `detects Amex`() {
        assertEquals(CardType.AMEX, CardType.detect("378282246310005"))
        assertEquals(CardType.AMEX, CardType.detect("371449635398431"))
    }

    @Test
    fun `returns unknown for unrecognized cards`() {
        assertEquals(CardType.UNKNOWN, CardType.detect("1234567890123456"))
    }

    @Test
    fun `CVV length is 4 for Amex`() {
        assertEquals(4, CardType.AMEX.cvvLength)
    }

    @Test
    fun `CVV length is 3 for other cards`() {
        assertEquals(3, CardType.VISA.cvvLength)
        assertEquals(3, CardType.MASTERCARD.cvvLength)
    }

    @Test
    fun `maps a stored card's brand whatever case the BIN database used`() {
        assertEquals(CardType.VISA, CardType.fromBrand("VISA"))
        assertEquals(CardType.VISA, CardType.fromBrand("visa"))
        assertEquals(CardType.MASTERCARD, CardType.fromBrand("MasterCard"))
        assertEquals(CardType.AMEX, CardType.fromBrand("AMEX"))
        assertEquals(CardType.AMEX, CardType.fromBrand("AMERICAN EXPRESS"))
        assertEquals(CardType.DISCOVER, CardType.fromBrand("Discover"))
    }

    @Test
    fun `maps the abbreviations the BIN database also emits`() {
        assertEquals(CardType.MASTERCARD, CardType.fromBrand("MC"))
        assertEquals(CardType.MASTERCARD, CardType.fromBrand("MASTER"))
    }

    @Test
    fun `brands without a type of their own fall back to unknown`() {
        // Their CVV is 3 digits, which is what UNKNOWN carries.
        assertEquals(CardType.UNKNOWN, CardType.fromBrand("JCB"))
        assertEquals(CardType.UNKNOWN, CardType.fromBrand("UNIONPAY"))
        assertEquals(CardType.UNKNOWN, CardType.fromBrand("MAESTRO"))
    }

    @Test
    fun `an absent or unresolved brand is unknown`() {
        // The backend writes the literal "unknown" when the BIN lookup found nothing.
        assertEquals(CardType.UNKNOWN, CardType.fromBrand("unknown"))
        assertEquals(CardType.UNKNOWN, CardType.fromBrand(null))
        assertEquals(CardType.UNKNOWN, CardType.fromBrand(""))
    }
}
