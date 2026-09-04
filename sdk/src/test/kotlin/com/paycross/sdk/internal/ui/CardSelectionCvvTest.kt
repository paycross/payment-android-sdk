package com.paycross.sdk.internal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A CVV belongs to the card it was typed for. If one survives a change of card,
 * the shopper submits card A's CVV against card B's token: the issuer's check
 * fails and the decline gives them nothing to correct, because the prompt for
 * card B sat over a box that already looked filled.
 */
class CardSelectionCvvTest {

    @Test
    fun `picking a stored card drops the CVV typed for a new card`() {
        assertEquals("", cvvAfterCardSelection(selectedUuid = null, pickedUuid = STORED_A, cvv = "737"))
    }

    @Test
    fun `going back to a new card drops the CVV typed for a stored one`() {
        assertEquals("", cvvAfterCardSelection(selectedUuid = STORED_A, pickedUuid = null, cvv = "737"))
    }

    @Test
    fun `moving between two stored cards drops the CVV`() {
        assertEquals("", cvvAfterCardSelection(selectedUuid = STORED_A, pickedUuid = STORED_B, cvv = "737"))
    }

    @Test
    fun `re-picking the card already selected is not a change and keeps the CVV`() {
        assertEquals("737", cvvAfterCardSelection(selectedUuid = STORED_A, pickedUuid = STORED_A, cvv = "737"))
        assertEquals("737", cvvAfterCardSelection(selectedUuid = null, pickedUuid = null, cvv = "737"))
    }

    @Test
    fun `an empty CVV stays empty across a change`() {
        assertEquals("", cvvAfterCardSelection(selectedUuid = null, pickedUuid = STORED_A, cvv = ""))
    }

    private companion object {
        const val STORED_A = "8f1c0b6e-0000-4000-8000-00000000000a"
        const val STORED_B = "8f1c0b6e-0000-4000-8000-00000000000b"
    }
}
