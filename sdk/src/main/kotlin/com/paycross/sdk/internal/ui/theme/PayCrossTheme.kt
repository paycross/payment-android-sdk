package com.paycross.sdk.internal.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

internal fun payCrossColorScheme(dark: Boolean, brand: Color?): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return if (brand == null) base else base.copy(primary = brand, onPrimary = onBrandColor(brand))
}

// Named onBrandColor rather than contentColorFor: Material 3 already exports a
// contentColorFor and the two would collide at every call site. 0.179 is the
// WCAG crossover, where black and white content contrast equally against the
// background; a midpoint of 0.5 would keep white text well past the point where
// black reads better.
internal fun onBrandColor(background: Color): Color =
    if (background.luminance() > 0.179f) Color.Black else Color.White

@Composable
internal fun PayCrossTheme(brand: Color?, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = payCrossColorScheme(isSystemInDarkTheme(), brand), content = content)
}
