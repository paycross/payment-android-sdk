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
}
