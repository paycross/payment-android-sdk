package com.paycross.sdk.internal.ui

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * The identifiers a merchant's UI tests address the sheet by.
 *
 * Internal only in the Kotlin sense: the strings are the contract, not the
 * constants, and iOS publishes the same names as accessibility identifiers, so a
 * merchant writes one selector per element for both platforms. Renaming one is a
 * breaking change and belongs in the changelog.
 *
 * They reach a UiAutomator tree only in a debuggable build — see
 * [rememberPublishTestTags] — because a release build would hand the sheet's
 * structure, and a saved card's uuid with it, to any accessibility service on
 * the device.
 *
 * A tag reaches a dump only if the flag is set in the same window. The sheet
 * sets it on its root and each dialog sets it on its own; the select field's
 * dropdown is a third window and sets it nowhere, so a tag attached in that
 * popup would be addressable from a Compose test and invisible to a dump.
 *
 * Three names in the cross-platform set have nothing to sit on here, and are
 * listed in the README as iOS-only rather than attached to something that only
 * approximates them:
 *
 * - `paycross.brand`, the brand badge iOS draws beside the card number.
 * - `paycross.threeDSCancel`, the Cancel button iOS puts over the challenge.
 * - `paycross.cancel`, the sheet's own cancel control. Android has none: the
 *   sheet is cancelled with the system back gesture, which `PaymentActivity`
 *   catches to raise the cancel dialog. A gesture has no node to tag, and
 *   tagging the sheet root as the cancel affordance would be a lie. The dialog
 *   it raises is tagged, and [CANCEL_DIALOG] with [CANCEL_CONFIRM] and
 *   [CANCEL_DISMISS] is the handle a test needs anyway.
 */
internal object TestTags {
    const val SHEET = "paycross.sheet"
    const val AMOUNT = "paycross.amount"
    /**
     * Google's Pay button.
     *
     * Sits on the Compose box wrapping the `AndroidView` that hosts Google's
     * `PayButton`, and must not be moved onto the `AndroidView` itself. A node
     * that hosts an Android view is replaced in its parent's child list by that
     * view, and the view publishes no resource id — so the tag would reach a
     * Compose test and no UiAutomator dump. That was #54.
     */
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

    /**
     * The banner's warning glyph. Not in the cross-platform set — it exists so a
     * test can prove the icon is drawn, since a decorative image carries no
     * semantics of its own to find it by.
     */
    const val ERROR_BANNER_ICON = "paycross.errorBanner.icon"
    const val PAY_BUTTON = "paycross.payButton"
    const val LOADING = "paycross.loading"
    /**
     * The 3-D Secure challenge.
     *
     * Sits on the Compose box `ThreeDsWebView` wraps its `AndroidView` in, and
     * must not be moved onto the `AndroidView` — see [WALLET_BUTTON] for why.
     */
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

    /**
     * One merchant-configured field, named by the group and field the server
     * sent.
     *
     * Joined with dots to match iOS, which builds the same string, so neither
     * half may contain one: a field called `city.error` in group `billing`
     * would otherwise produce the identifier of `city`'s error node. A space
     * would likewise put a space in a resource id. The README says so; the
     * codebase keys the same pair with a pipe internally
     * (`FieldGroupInputs.kt`) precisely because a dot is not separator-safe,
     * and only the cross-platform contract keeps this one on dots.
     */
    fun field(group: String, name: String): String = "paycross.field.$group.$name"

    /**
     * A field's validation message. It sits inside the field's own merged node,
     * so a Compose test reads it from the unmerged tree and a UiAutomator dump
     * does not see it at all — there, the field's error state is the signal.
     */
    fun fieldError(group: String, name: String): String = "${field(group, name)}.error"
}

/**
 * Whether this build may publish its test tags as resource ids.
 *
 * Read per window rather than once for the sheet: Compose walks a node's parents
 * looking for the flag and stops at the semantics root it is in, and a dialog is
 * a window with a root of its own — so a flag set only on the sheet leaves every
 * dialog tag out of a UiAutomator dump while a Compose test, which sees every
 * window, still finds it. Green tests are not evidence for this one.
 */
@Composable
internal fun rememberPublishTestTags(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
