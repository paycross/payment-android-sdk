package com.paycross.sdk.internal.ui

import com.paycross.sdk.internal.api.models.SavedCard
import com.paycross.sdk.internal.validation.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A stored card's CVV length comes from the brand it was saved with. Getting it
 * from anywhere else makes a saved Amex unusable: its 4-digit CID cannot be
 * typed and never validates, so the Pay button stays disabled forever.
 */
class CardFormValidationTest {

    @Test
    fun `a stored card takes its CVV length from the brand it was saved with`() {
        assertEquals(CardType.AMEX, cvvCardTypeFor(savedCard(brand = "AMEX")))
        assertEquals(CardType.VISA, cvvCardTypeFor(savedCard(brand = "VISA")))
    }

    @Test
    fun `a stored Amex accepts its 4-digit CID and rejects 3`() {
        val amex = savedCard(brand = "AMEX")

        assertTrue(validateStoredCardCvv("1234", amex).isCvvValid)
        assertFalse(validateStoredCardCvv("123", amex).isCvvValid)
    }

    @Test
    fun `a stored Visa still holds at 3 digits`() {
        val visa = savedCard(brand = "VISA")

        assertTrue(validateStoredCardCvv("123", visa).isCvvValid)
        assertFalse(validateStoredCardCvv("1234", visa).isCvvValid)
    }

    @Test
    fun `a stored card with no usable brand holds at 3 digits`() {
        val unresolved = savedCard(brand = "unknown")

        assertTrue(validateStoredCardCvv("123", unresolved).isCvvValid)
        assertFalse(validateStoredCardCvv("1234", unresolved).isCvvValid)
    }

    @Test
    fun `a new card still takes its CVV length from the number being typed`() {
        assertEquals(CardType.AMEX, cvvCardType(isNewCard = true, CardType.AMEX, savedCard = null))
        assertEquals(CardType.VISA, cvvCardType(isNewCard = true, CardType.VISA, savedCard = null))
    }

    @Test
    fun `a stored card needs only its CVV, not the new-card fields`() {
        val validation = validateStoredCardCvv("1234", savedCard(brand = "AMEX"))

        assertTrue(validation.isValid)
    }

    // The entered type is UNKNOWN on this path: the PAN field is not on screen
    // when a stored card is selected, so only the stored brand can supply it.
    private fun cvvCardTypeFor(card: SavedCard): CardType =
        cvvCardType(isNewCard = false, enteredCardType = CardType.UNKNOWN, savedCard = card)

    private fun validateStoredCardCvv(cvv: String, card: SavedCard): FormValidation =
        validateForm(
            isNewCard = false,
            cardNumber = "",
            expiry = "",
            cvv = cvv,
            cardholderName = "",
            cvvCardType = cvvCardTypeFor(card)
        )

    private fun savedCard(brand: String?) = SavedCard(
        uuid = "8f1c0b6e-0000-4000-8000-000000000001",
        maskedPan = "3782 82****0005",
        cardBrand = brand,
        expireMonth = "12",
        expireYear = "2030",
        cardholderName = "JOHN DOE"
    )
}
