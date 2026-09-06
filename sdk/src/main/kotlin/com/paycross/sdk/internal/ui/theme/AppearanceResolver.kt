package com.paycross.sdk.internal.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.paycross.sdk.PayCrossAppearance
import com.paycross.sdk.PayCrossColors
import com.paycross.sdk.ThemeMode

private const val MIN_SCALE = 0.8f
private const val MAX_SCALE = 1.3f
private const val MIN_CONTRAST = 4.5f
private const val HEX_SHORT = 3
private const val HEX_FULL = 6
private const val OPAQUE = 0xFF000000L
private const val RGB_MASK = 0xFFFFFF

/**
 * Radii and thickness in dp. Null means Material's own value stands.
 */
internal data class ResolvedShapes(
    val cornerRadius: Dp?,
    val buttonCornerRadius: Dp?,
    val borderWidth: Dp?
)

/**
 * Pay button overrides. Null means the caller's fallback stands — the brand for
 * the background, `onBrand` for the label, Material for the disabled pair.
 */
internal data class ResolvedPrimaryButton(
    val background: Color?,
    val textColor: Color?,
    val disabledBackground: Color?,
    val disabledTextColor: Color?,
    val cornerRadius: Dp?,
    val height: Dp?
)

/**
 * Everything the sheet's theme needs, for one mode.
 *
 * [colorScheme] carries the roles Material has a slot for, already merged with
 * the platform scheme, so a role nobody set is simply the Material colour.
 * [component] and [placeholder] have no scheme slot the outlined fields read —
 * their container is transparent by default — so they stay nullable and the
 * field seam falls back to Material when they are null.
 */
internal data class ResolvedAppearance(
    val dark: Boolean,
    val colorScheme: ColorScheme,
    val component: Color?,
    val placeholder: Color?,
    val icon: Color,
    val shapes: ResolvedShapes,
    val primaryButton: ResolvedPrimaryButton,
    val sizeScaleFactor: Float
)

/**
 * Merges the merchant's appearance, the back office's brand colour and the
 * platform defaults into one resolved theme, per role: what the merchant set in
 * code wins, then the server's brand colour, then Material.
 *
 * Pure by design — no Android framework types, no composition — so the whole
 * precedence table is covered by JVM tests rather than on an emulator.
 */
internal object AppearanceResolver {

    fun resolve(
        appearance: PayCrossAppearance?,
        serverBrandHex: String?,
        systemDark: Boolean
    ): ResolvedAppearance {
        val dark = isDark(appearance?.themeMode ?: ThemeMode.SYSTEM, systemDark)
        val colors = (if (dark) appearance?.dark else appearance?.light) ?: PayCrossColors()
        val base = if (dark) darkColorScheme() else lightColorScheme()

        val brand = colors.brand?.let(::Color) ?: parseHexColor(serverBrandHex)
        val onBrand = colors.onBrand?.let(::Color) ?: brand?.let(::onBrandColor)
        val surface = colors.surface?.let(::Color)
        val component = colors.component?.let(::Color)
        val text = colors.text?.let(::Color)
        val textSecondary = colors.textSecondary?.let(::Color) ?: base.onSurfaceVariant

        val scheme = base.copy(
            primary = brand ?: base.primary,
            onPrimary = onBrand ?: base.onPrimary,
            background = surface ?: base.background,
            surface = surface ?: base.surface,
            // The dialog and menu container, which is the one place `component`
            // maps onto a Material slot; the fields read it through the seam.
            surfaceContainerHigh = component ?: base.surfaceContainerHigh,
            outline = colors.componentBorder?.let(::Color) ?: base.outline,
            onBackground = text ?: base.onBackground,
            onSurface = text ?: base.onSurface,
            onSurfaceVariant = textSecondary,
            error = colors.error?.let(::Color) ?: base.error
        )

        val shapes = resolveShapes(appearance)

        return ResolvedAppearance(
            dark = dark,
            colorScheme = scheme,
            component = component,
            placeholder = colors.placeholder?.let(::Color),
            icon = colors.icon?.let(::Color) ?: textSecondary,
            shapes = shapes,
            primaryButton = resolvePrimaryButton(appearance, shapes),
            sizeScaleFactor = appearance?.typography?.sizeScaleFactor
                ?.coerceIn(MIN_SCALE, MAX_SCALE) ?: 1f
        )
    }

    fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Named onBrandColor rather than contentColorFor: Material 3 already exports a
    // contentColorFor and the two would collide at every call site. 0.179 is the
    // WCAG crossover, where black and white content contrast equally against the
    // background; a midpoint of 0.5 would keep white text well past the point where
    // black reads better.
    fun onBrandColor(background: Color): Color =
        if (background.luminance() > 0.179f) Color.Black else Color.White

    /**
     * `#RRGGBB` or `#RGB`, with or without the hash and in either case. Anything
     * else is null: a brand colour the back office cannot express costs the
     * colour, not the sheet.
     */
    fun parseHexColor(hex: String?): Color? {
        val digits = hex?.trim()?.removePrefix("#") ?: return null
        if (digits.length != HEX_SHORT && digits.length != HEX_FULL) return null
        if (!digits.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null

        val full = if (digits.length == HEX_SHORT) {
            digits.flatMap { listOf(it, it) }.joinToString("")
        } else {
            digits
        }
        return Color(full.toLong(16) or OPAQUE)
    }

    /**
     * Colour pairs the sheet will draw one on the other that fall below the WCAG
     * AA ratio for body text. Returned rather than logged so the rule is a unit
     * test, and so the caller can keep the warning out of release builds.
     */
    fun contrastWarnings(resolved: ResolvedAppearance): List<String> = buildList {
        val brand = resolved.colorScheme.primary
        val onBrand = resolved.colorScheme.onPrimary
        if (contrastRatio(brand, onBrand) < MIN_CONTRAST) {
            add(warning("brand", brand, onBrand))
        }

        val button = resolved.primaryButton
        val background = button.background ?: brand
        val label = button.textColor ?: onBrand
        if ((button.background != null || button.textColor != null) &&
            contrastRatio(background, label) < MIN_CONTRAST
        ) {
            add(warning("primary button", background, label))
        }
    }

    private fun warning(pair: String, background: Color, content: Color): String =
        "PayCrossAppearance: #${content.toHex()} on #${background.toHex()} is below the " +
            "4.5:1 contrast ratio the $pair needs to stay readable"

    private fun contrastRatio(a: Color, b: Color): Float {
        val lighter = maxOf(a.luminance(), b.luminance())
        val darker = minOf(a.luminance(), b.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun Color.toHex(): String =
        (toArgb() and RGB_MASK).toString(16).padStart(HEX_FULL, '0').uppercase()

    private fun resolveShapes(appearance: PayCrossAppearance?): ResolvedShapes {
        val shapes = appearance?.shapes
        val cornerRadius = shapes?.cornerRadius?.dp
        return ResolvedShapes(
            cornerRadius = cornerRadius,
            buttonCornerRadius = shapes?.buttonCornerRadius?.dp ?: cornerRadius,
            borderWidth = shapes?.borderWidth?.dp
        )
    }

    private fun resolvePrimaryButton(
        appearance: PayCrossAppearance?,
        shapes: ResolvedShapes
    ): ResolvedPrimaryButton {
        val button = appearance?.primaryButton
        return ResolvedPrimaryButton(
            background = button?.background?.let(::Color),
            textColor = button?.textColor?.let(::Color),
            disabledBackground = button?.disabledBackground?.let(::Color),
            disabledTextColor = button?.disabledTextColor?.let(::Color),
            cornerRadius = button?.cornerRadius?.dp ?: shapes.buttonCornerRadius,
            height = button?.height?.dp
        )
    }
}
