package com.paycross.sdk.internal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every identifier's literal value, written out once more.
 *
 * These strings are the contract a merchant's UI tests are written against and
 * iOS publishes the same set, so a rename is a breaking change rather than a
 * refactor — and a constant renamed in place would otherwise agree with every
 * call site at once. The rendered-sheet test covers most of them by literal too,
 * but only for elements a Compose test can stand up: the sheet root and the
 * 3-D Secure view live inside `PaymentScreen`, which needs a session and a
 * payment in flight, and they are the two the E2E driver leans on hardest.
 */
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
        // Not a behaviour to rely on — the documented constraint is that neither
        // half carries a dot. Pinned so that anyone who later sanitizes the
        // builders, on both platforms at once, finds this test rather than a
        // merchant finding the collision.
        assertEquals(
            TestTags.fieldError("billing", "city"),
            TestTags.field("billing", "city.error")
        )
    }
}
