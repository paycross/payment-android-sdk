package com.paycross.sdk.internal.ui.components

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.wallet.button.ButtonConstants
import com.google.android.gms.wallet.button.ButtonOptions
import com.google.android.gms.wallet.button.PayButton
import com.paycross.sdk.R
import com.paycross.sdk.internal.ui.pcStringResource
import com.paycross.sdk.internal.ui.theme.LocalPayCrossAppearance

internal const val GOOGLE_PAY_BUTTON_TAG = "google_pay_button"

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
        AndroidView(
            factory = { context -> PayButton(context) },
            // initialize, not just the listener: it clears the view and rebuilds
            // from the options, so a changed theme repaints instead of keeping
            // whatever the first composition drew.
            update = { button ->
                button.initialize(buttonOptions)
                button.setOnClickListener { currentOnClick() }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag(GOOGLE_PAY_BUTTON_TAG)
        )

        OrPayWithCardDivider(modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun OrPayWithCardDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
