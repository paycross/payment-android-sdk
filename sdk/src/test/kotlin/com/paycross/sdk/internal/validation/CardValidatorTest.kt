package com.paycross.sdk.internal.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardValidatorTest {

    @Test
    fun `valid card number passes Luhn check`() {
        assertTrue(CardValidator.isValidCardNumber("4111111111111111")) // Visa test
        assertTrue(CardValidator.isValidCardNumber("5500000000000004")) // Mastercard test
    }

    @Test
    fun `invalid card number fails Luhn check`() {
        assertFalse(CardValidator.isValidCardNumber("4532010000000367"))
        assertFalse(CardValidator.isValidCardNumber("1234567890123456"))
    }

    @Test
    fun `card number with spaces is validated`() {
        assertTrue(CardValidator.isValidCardNumber("4111 1111 1111 1111"))
    }

    @Test
    fun `card number too short fails`() {
        assertFalse(CardValidator.isValidCardNumber("411111111111"))
    }

    @Test
    fun `card number too long fails`() {
        assertFalse(CardValidator.isValidCardNumber("41111111111111111111"))
    }

    @Test
    fun `card number with letters fails`() {
        assertFalse(CardValidator.isValidCardNumber("4111ABCD11111111"))
    }

    @Test
    fun `valid expiry passes`() {
        assertTrue(CardValidator.isValidExpiry("12", "2030"))
    }

    @Test
    fun `valid expiry with two digit year passes`() {
        assertTrue(CardValidator.isValidExpiry("12", "30"))
    }

    @Test
    fun `expired card fails`() {
        assertFalse(CardValidator.isValidExpiry("12", "2020"))
    }

    @Test
    fun `invalid month fails`() {
        assertFalse(CardValidator.isValidExpiry("13", "2030"))
        assertFalse(CardValidator.isValidExpiry("00", "2030"))
    }

    @Test
    fun `non-numeric expiry fails`() {
        assertFalse(CardValidator.isValidExpiry("AB", "2030"))
        assertFalse(CardValidator.isValidExpiry("12", "ABCD"))
    }

    @Test
    fun `valid CVV passes for Visa`() {
        assertTrue(CardValidator.isValidCvv("123", CardType.VISA))
    }

    @Test
    fun `valid CVV passes for Amex`() {
        assertTrue(CardValidator.isValidCvv("1234", CardType.AMEX))
    }

    @Test
    fun `invalid CVV length fails`() {
        assertFalse(CardValidator.isValidCvv("12", CardType.VISA))
        assertFalse(CardValidator.isValidCvv("123", CardType.AMEX))
    }

    @Test
    fun `CVV with letters fails`() {
        assertFalse(CardValidator.isValidCvv("12A", CardType.VISA))
    }

    @Test
    fun `valid cardholder name passes`() {
        assertTrue(CardValidator.isValidCardholderName("John Doe"))
    }

    @Test
    fun `cardholder name with hyphen passes`() {
        assertTrue(CardValidator.isValidCardholderName("Mary-Jane Watson"))
    }

    @Test
    fun `empty cardholder name fails`() {
        assertFalse(CardValidator.isValidCardholderName(""))
        assertFalse(CardValidator.isValidCardholderName("   "))
    }
}
