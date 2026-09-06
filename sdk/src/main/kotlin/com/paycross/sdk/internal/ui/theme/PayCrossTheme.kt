package com.paycross.sdk.internal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified

/**
 * The resolved appearance, for the parts of it Material has no slot for: the
 * fields' container and placeholder colours, the shapes, the Pay button's
 * overrides and the mode the Google Pay button has to match.
 *
 * Null outside [PayCrossTheme], where every reader falls back to Material.
 */
internal val LocalPayCrossAppearance = staticCompositionLocalOf<ResolvedAppearance?> { null }

/**
 * The `icon` role. Separate from [LocalPayCrossAppearance] because icons ask for
 * one colour and nothing else, and because `textSecondary` — which it defaults
 * to — stays a text colour.
 */
internal val LocalIconTint = staticCompositionLocalOf { Color.Unspecified }

@Composable
internal fun PayCrossTheme(appearance: ResolvedAppearance, content: @Composable () -> Unit) {
    val shapes = remember(appearance.shapes.cornerRadius) {
        payCrossShapes(appearance.shapes.cornerRadius)
    }
    val typography = remember(appearance.sizeScaleFactor) {
        payCrossTypography(appearance.sizeScaleFactor)
    }

    CompositionLocalProvider(
        LocalPayCrossAppearance provides appearance,
        LocalIconTint provides appearance.icon
    ) {
        MaterialTheme(
            colorScheme = appearance.colorScheme,
            shapes = shapes,
            typography = typography,
            content = content
        )
    }
}

/**
 * `cornerRadius` covers the small components the sheet draws — the input fields
 * and the menu the picker's dropdown opens. Dialogs keep Material's own radius:
 * they are chrome around the sheet rather than part of it, and a field radius
 * reads wrong at that size.
 */
private fun payCrossShapes(cornerRadius: Dp?): Shapes {
    if (cornerRadius == null) return Shapes()
    val shape = RoundedCornerShape(cornerRadius)
    return Shapes(extraSmall = shape, small = shape)
}

/**
 * Multiplies every type size by the resolved factor. Sizes stay in sp, so this
 * composes with the device's own font scale rather than replacing it.
 */
private fun payCrossTypography(sizeScaleFactor: Float): Typography {
    val base = Typography()
    if (sizeScaleFactor == 1f) return base
    return Typography(
        displayLarge = base.displayLarge.scaledBy(sizeScaleFactor),
        displayMedium = base.displayMedium.scaledBy(sizeScaleFactor),
        displaySmall = base.displaySmall.scaledBy(sizeScaleFactor),
        headlineLarge = base.headlineLarge.scaledBy(sizeScaleFactor),
        headlineMedium = base.headlineMedium.scaledBy(sizeScaleFactor),
        headlineSmall = base.headlineSmall.scaledBy(sizeScaleFactor),
        titleLarge = base.titleLarge.scaledBy(sizeScaleFactor),
        titleMedium = base.titleMedium.scaledBy(sizeScaleFactor),
        titleSmall = base.titleSmall.scaledBy(sizeScaleFactor),
        bodyLarge = base.bodyLarge.scaledBy(sizeScaleFactor),
        bodyMedium = base.bodyMedium.scaledBy(sizeScaleFactor),
        bodySmall = base.bodySmall.scaledBy(sizeScaleFactor),
        labelLarge = base.labelLarge.scaledBy(sizeScaleFactor),
        labelMedium = base.labelMedium.scaledBy(sizeScaleFactor),
        labelSmall = base.labelSmall.scaledBy(sizeScaleFactor)
    )
}

private fun TextStyle.scaledBy(factor: Float): TextStyle =
    copy(fontSize = fontSize.scaledBy(factor), lineHeight = lineHeight.scaledBy(factor))

private fun TextUnit.scaledBy(factor: Float): TextUnit = if (isSpecified) this * factor else this
