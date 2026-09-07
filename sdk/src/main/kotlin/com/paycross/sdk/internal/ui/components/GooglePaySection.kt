package com.paycross.sdk.internal.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.wallet.button.ButtonConstants
import com.google.android.gms.wallet.button.ButtonOptions
import com.google.android.gms.wallet.button.PayButton
import com.paycross.sdk.R
import com.paycross.sdk.internal.ui.TestTags
import com.paycross.sdk.internal.ui.pcStringResource
import com.paycross.sdk.internal.ui.theme.LocalPayCrossAppearance

/**
 * The button variant Google pairs with a surface of the given mode: a dark
 * button on a light sheet, a light button on a dark one. The fixed DARK this
 * replaces left the button invisible against the SDK's dark surface.
 */
internal fun googlePayButtonTheme(dark: Boolean): Int =
    if (dark) ButtonConstants.ButtonTheme.LIGHT else ButtonConstants.ButtonTheme.DARK

/**
 * Google's official Pay button above an "Or pay with card" divider.
 *
 * The button is the PayButton view from play-services-wallet wrapped in an
 * AndroidView — Google's brand guidelines prohibit custom markup, the SDK owns
 * the button's rendering. The compose-pay-button wrapper artifact does the
 * same thing but drags in androidx.core 1.15.0, which demands compileSdk 35;
 * this project pins 34, so the view is wrapped directly.
 */
@Composable
internal fun GooglePaySection(
    allowedPaymentMethodsJson: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val appearance = LocalPayCrossAppearance.current
    val buttonTheme = googlePayButtonTheme(dark = appearance?.dark == true)
    // The radius is the only property of the wallet button that is ours to set;
    // its colours and label belong to Google's brand guidelines. It is asked for
    // in pixels, unlike everything else the sheet draws.
    val density = LocalDensity.current
    val cornerRadiusPx = appearance?.shapes?.buttonCornerRadius?.let {
        with(density) { it.roundToPx() }
    }
    val buttonOptions = remember(allowedPaymentMethodsJson, buttonTheme, cornerRadiusPx) {
        ButtonOptions.newBuilder()
            .setButtonType(ButtonConstants.ButtonType.PLAIN)
            .setButtonTheme(buttonTheme)
            .setAllowedPaymentMethods(allowedPaymentMethodsJson)
            .apply { cornerRadiusPx?.let { setCornerRadius(it) } }
            .build()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // The tag hangs on this Box, not on the AndroidView inside it.
        // testTagsAsResourceId writes the resource id onto Compose's own
        // semantics nodes, and a node hosting an Android view hands its
        // accessibility node to that view instead — so a tag down there is
        // visible to a Compose test and absent from every UiAutomator dump. The
        // Box carries the button's size and propagates it, leaving the measured
        // result exactly what the AndroidView had before.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag(TestTags.WALLET_BUTTON),
            propagateMinConstraints = true
        ) {
            AndroidView(
                factory = { context -> PayButton(context) },
                // initialize, not just the listener: it clears the view and
                // rebuilds from the options, so a changed theme repaints instead
                // of keeping whatever the first composition drew.
                update = { button ->
                    button.initialize(buttonOptions)
                    button.setOnClickListener { currentOnClick() }
                }
            )
        }

        OrPayWithCardDivider(
            modifier = Modifier
                .padding(top = 16.dp)
                .testTag(TestTags.WALLET_DIVIDER)
        )
    }
}

@Composable
private fun OrPayWithCardDivider(modifier: Modifier = Modifier) {
    Row(
        // Merged so the caption names the whole rule rather than leaving three
        // nodes, two of them decorative, for a screen reader to step through.
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = pcStringResource(R.string.paycross_or_pay_with_card),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}
