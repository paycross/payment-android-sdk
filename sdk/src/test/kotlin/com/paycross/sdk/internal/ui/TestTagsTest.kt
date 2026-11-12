package com.paycross.sdk.internal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TestTagsTest {

    @Test
    fun everyIdentifierHasTheValueTheContractPromises() {
        assertEquals("paycross.sheet", TestTags.SHEET)
        assertEquals("paycross.amount", TestTags.AMOUNT)
        assertEquals("paycross.walletButton", TestTags.WALLET_BUTTON)
        assertEquals("paycross.walletDivider", TestTags.WALLET_DIVIDER)
        assertEquals("paycross.savedCards", TestTags.SAVED_CARDS)
        assertEquals("paycross.useNewCard", TestTags.USE_NEW_CARD)
        assertEquals("paycross.cardNumber", TestTags.CARD_NUMBER)
        assertEquals("paycross.expiry", TestTags.EXPIRY)
        assertEquals("paycross.cvv", TestTags.CVV)
        assertEquals("paycross.cardholderName", TestTags.CARDHOLDER_NAME)
        assertEquals("paycross.saveCard", TestTags.SAVE_CARD)
        assertEquals("paycross.errorBanner", TestTags.ERROR_BANNER)
        assertEquals("paycross.errorBanner.icon", TestTags.ERROR_BANNER_ICON)
        assertEquals("paycross.payButton", TestTags.PAY_BUTTON)
        assertEquals("paycross.loading", TestTags.LOADING)
        assertEquals("paycross.threeDS", TestTags.THREE_DS)
        assertEquals("paycross.cancelDialog", TestTags.CANCEL_DIALOG)
        assertEquals("paycross.cancelConfirm", TestTags.CANCEL_CONFIRM)
        assertEquals("paycross.cancelDismiss", TestTags.CANCEL_DISMISS)
        assertEquals("paycross.removeDialog", TestTags.REMOVE_DIALOG)
        assertEquals("paycross.removeConfirm", TestTags.REMOVE_CONFIRM)
        assertEquals("paycross.removeDismiss", TestTags.REMOVE_DISMISS)
    }

    @Test
    fun theBuildersNameACardAndAServerField() {
        val uuid = "0f8c1b2e-3d4a-5b6c-7d8e-9f0a1b2c3d4e"
        assertEquals("paycross.savedCard.$uuid", TestTags.savedCard(uuid))
        assertEquals("paycross.savedCard.$uuid.delete", TestTags.savedCardDelete(uuid))
        assertEquals("paycross.field.billing.city", TestTags.field("billing", "city"))
        assertEquals(
            "paycross.field.billing.city.error",
            TestTags.fieldError("billing", "city")
        )
    }

    @Test
    fun aDottedFieldNameCollidesWithAnErrorNode() {
        assertEquals(
            TestTags.fieldError("billing", "city"),
            TestTags.field("billing", "city.error")
        )
    }
}
