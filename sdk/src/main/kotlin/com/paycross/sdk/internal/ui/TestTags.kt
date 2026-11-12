package com.paycross.sdk.internal.ui

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal object TestTags {
    const val SHEET = "paycross.sheet"
    const val AMOUNT = "paycross.amount"
    // WHY: Tag the Compose box around the AndroidView, not the view: an AndroidView replaces its node in the parent's child list and has no resource id.
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

    const val ERROR_BANNER_ICON = "paycross.errorBanner.icon"
    const val PAY_BUTTON = "paycross.payButton"
    const val LOADING = "paycross.loading"
    const val THREE_DS = "paycross.threeDS"
    const val CANCEL_DIALOG = "paycross.cancelDialog"
    const val CANCEL_CONFIRM = "paycross.cancelConfirm"
    const val CANCEL_DISMISS = "paycross.cancelDismiss"
    const val REMOVE_DIALOG = "paycross.removeDialog"
    const val REMOVE_CONFIRM = "paycross.removeConfirm"
    const val REMOVE_DISMISS = "paycross.removeDismiss"

    fun savedCard(uuid: String): String = "paycross.savedCard.$uuid"

    fun savedCardDelete(uuid: String): String = "${savedCard(uuid)}.delete"

    fun field(group: String, name: String): String = "paycross.field.$group.$name"

    fun groupOptIn(group: String): String = "paycross.group.$group.optIn"

    fun fieldError(group: String, name: String): String = errorFor(field(group, name))

    fun errorFor(tag: String): String = "$tag.error"
}

@Composable
internal fun rememberPublishTestTags(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }
}
