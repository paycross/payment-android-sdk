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

internal fun googlePayButtonTheme(dark: Boolean): Int =
    if (dark) ButtonConstants.ButtonTheme.LIGHT else ButtonConstants.ButtonTheme.DARK

// The PayButton view is wrapped directly: Google's brand guidelines prohibit custom markup, and the compose wrapper needs compileSdk 35.
@Composable
internal fun GooglePaySection(
    allowedPaymentMethodsJson: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val appearance = LocalPayCrossAppearance.current
    val buttonTheme = googlePayButtonTheme(dark = appearance?.dark == true)
    // Only the corner radius is set here; Google's guidelines own the colours and label, and the radius is in pixels.
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
        // The Box holds the size and tag and propagates its constraints to the AndroidView inside it.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag(TestTags.WALLET_BUTTON),
            propagateMinConstraints = true
        ) {
            AndroidView(
                factory = { context -> PayButton(context) },
                // Initialize, not only the listener: it rebuilds the view from the options, so a changed theme repaints.
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
        // Merged so a screen reader reads the caption as one node.
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
