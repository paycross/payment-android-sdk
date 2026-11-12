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

internal data class ResolvedShapes(
    val cornerRadius: Dp?,
    val buttonCornerRadius: Dp?,
    val borderWidth: Dp?
)

internal data class ResolvedPrimaryButton(
    val background: Color?,
    val textColor: Color?,
    val disabledBackground: Color?,
    val disabledTextColor: Color?,
    val cornerRadius: Dp?,
    val height: Dp?
)

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
            sizeScaleFactor = appearance?.typography?.sizeScaleFactor.asScale()
        )
    }

    fun isDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Not named contentColorFor, which Material 3 already exports.
    // 0.179 is the WCAG crossover where black and white content contrast equally.
    fun onBrandColor(background: Color): Color =
        if (background.luminance() > 0.179f) Color.Black else Color.White

    // iOS parses the same two shapes, so eight-digit hex is refused.
    fun parseHexColor(hex: String?): Color? {
        val trimmed = hex?.trim() ?: return null
        if (!trimmed.startsWith("#")) return null

        val digits = trimmed.removePrefix("#")
        if (digits.length != HEX_SHORT && digits.length != HEX_FULL) return null
        if (!digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null

        val full = if (digits.length == HEX_SHORT) {
            digits.flatMap { listOf(it, it) }.joinToString("")
        } else {
            digits
        }
        return Color(full.toLong(16) or OPAQUE)
    }

    fun contrastWarnings(resolved: ResolvedAppearance): List<String> = buildList {
        val brand = resolved.colorScheme.primary
        val onBrand = resolved.colorScheme.onPrimary
        if (contrastRatio(brand, onBrand) < MIN_CONTRAST) {
            add(warning("brand", brand, onBrand))
        }

        val surface = resolved.colorScheme.surface
        val onSurface = resolved.colorScheme.onSurface
        if (contrastRatio(surface, onSurface) < MIN_CONTRAST) {
            add(warning("sheet", surface, onSurface))
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
        val cornerRadius = shapes?.cornerRadius.asDimension()
        return ResolvedShapes(
            cornerRadius = cornerRadius,
            buttonCornerRadius = shapes?.buttonCornerRadius.asDimension() ?: cornerRadius,
            borderWidth = shapes?.borderWidth.asDimension()
        )
    }

    private fun resolvePrimaryButton(
        appearance: PayCrossAppearance?,
        shapes: ResolvedShapes
    ): ResolvedPrimaryButton {
        val button = appearance?.primaryButton
        val background = button?.background?.let(::Color)
        return ResolvedPrimaryButton(
            background = background,
            textColor = button?.textColor?.let(::Color) ?: background?.let(::onBrandColor),
            disabledBackground = button?.disabledBackground?.let(::Color),
            disabledTextColor = button?.disabledTextColor?.let(::Color),
            cornerRadius = button?.cornerRadius.asDimension() ?: shapes.buttonCornerRadius,
            height = button?.height.asDimension()
        )
    }

    // NaN passes coerceIn and the Dp constructor, and Dp.roundToPx throws on it.
    private fun Float?.asDimension(): Dp? = this?.takeIf { it.isFinite() && it >= 0f }?.dp

    private fun Float?.asScale(): Float =
        this?.takeIf { it.isFinite() }?.coerceIn(MIN_SCALE, MAX_SCALE) ?: 1f
}
