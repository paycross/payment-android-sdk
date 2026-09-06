package com.paycross.sdk.internal.ui

/**
 * The identifiers a merchant's UI tests address the sheet by.
 *
 * Internal only in the Kotlin sense: the strings are the contract, not the
 * constants, and iOS publishes the same names as accessibility identifiers, so a
 * merchant writes one selector per element for both platforms. Renaming one is a
 * breaking change and belongs in the changelog.
 *
 * They reach a UiAutomator tree only in a debuggable build — `PaymentActivity`
 * gates `testTagsAsResourceId` on `FLAG_DEBUGGABLE`, because a release build
 * would hand the sheet's structure, and a saved card's uuid with it, to any
 * accessibility service on the device.
 *
 * Two names in the cross-platform set have nothing to sit on here:
 * `paycross.brand` and `paycross.threeDSCancel`. iOS draws a brand badge beside
 * the card number and a Cancel button over the 3-D Secure challenge; Android
 * draws neither, and the challenge is dismissed with the system back gesture
 * into the cancel dialog. They are listed in the README as iOS-only rather than
 * attached to something that only approximates them.
 */
internal object TestTags {
    const val SHEET = "paycross.sheet"
    const val AMOUNT = "paycross.amount"
    const val WALLET_BUTTON = "paycross.walletButton"
    const val WALLET_DIVIDER = "paycross.walletDivider"
    const val SAVED_CARDS = "paycross.savedCards"
    const val USE_NEW_CARD = "paycross.useNewCard"
    const val CARD_NUMBER = "paycross.cardNumber"
    const val EXPIRY = "paycross.expiry"
    const val CVV = "paycross.cvv"
    const val CARDHOLDER_NAME = "paycross.cardholderName"
    const val SAVE_CARD = "paycross.saveCard"
    const val ERROR_BANNER = "paycross.errorBanner"
    const val PAY_BUTTON = "paycross.payButton"
    const val LOADING = "paycross.loading"
    const val THREE_DS = "paycross.threeDS"
    const val CANCEL_DIALOG = "paycross.cancelDialog"
    const val CANCEL_CONFIRM = "paycross.cancelConfirm"
    const val CANCEL_DISMISS = "paycross.cancelDismiss"
    const val REMOVE_DIALOG = "paycross.removeDialog"
    const val REMOVE_CONFIRM = "paycross.removeConfirm"
    const val REMOVE_DISMISS = "paycross.removeDismiss"

    /** The row for one stored card. [uuid] is the wire's `uuid`, iOS's `card.id`. */
    fun savedCard(uuid: String): String = "paycross.savedCard.$uuid"

    fun savedCardDelete(uuid: String): String = "${savedCard(uuid)}.delete"

    /** One merchant-configured field, named by the group and field the server sent. */
    fun field(group: String, name: String): String = "paycross.field.$group.$name"

    /**
     * A field's validation message. It sits inside the field's own merged node,
     * so a Compose test reads it from the unmerged tree and a UiAutomator dump
     * does not see it at all — there, the field's error state is the signal.
     */
    fun fieldError(group: String, name: String): String = "${field(group, name)}.error"
}
