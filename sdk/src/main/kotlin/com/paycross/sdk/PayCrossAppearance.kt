package com.paycross.sdk

import androidx.annotation.ColorInt

/**
 * Chooses the light or dark palette for the sheet. The host app's own theme is never changed.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * One colour palette, used for light or dark mode. Null roles keep the platform default.
 *
 * @property onBrand Text and spinner colour on [brand]. Null derives it from the luminance of [brand].
 */
data class PayCrossColors(
    @ColorInt val brand: Int? = null,
    @ColorInt val onBrand: Int? = null,
    @ColorInt val surface: Int? = null,
    @ColorInt val component: Int? = null,
    @ColorInt val componentBorder: Int? = null,
    @ColorInt val text: Int? = null,
    @ColorInt val textSecondary: Int? = null,
    @ColorInt val placeholder: Int? = null,
    @ColorInt val icon: Int? = null,
    @ColorInt val error: Int? = null
)

/**
 * Corner radii and border width, in dp. Null keeps the Material value.
 *
 * @property buttonCornerRadius Pay and wallet button corner radius. Falls back to [cornerRadius].
 * @property borderWidth Input border width in both focus states.
 */
data class PayCrossShapes(
    val cornerRadius: Float? = null,
    val buttonCornerRadius: Float? = null,
    val borderWidth: Float? = null
)

/**
 * Pay button overrides. Null roles fall back to the matching palette role, then to Material.
 *
 * @property height Minimum height in dp. The button grows when the label needs more room.
 */
data class PayCrossPrimaryButton(
    @ColorInt val background: Int? = null,
    @ColorInt val textColor: Int? = null,
    @ColorInt val disabledBackground: Int? = null,
    @ColorInt val disabledTextColor: Int? = null,
    val cornerRadius: Float? = null,
    val height: Float? = null
)

/**
 * Text sizes in the sheet.
 *
 * @property sizeScaleFactor Multiplies every font size, on top of the device font scale. Clamped to 0.8–1.3.
 */
data class PayCrossTypography(
    val sizeScaleFactor: Float? = null
)

/**
 * Visual settings for the payment sheet. Colours resolve per role: the value set here, then the
 * merchant's back-office brand colour, then the platform default. Layout, the 3-D Secure page and
 * the wallet button labels are fixed.
 *
 * ```
 * PayCross.init(
 *     environment = PayCrossEnvironment.PRODUCTION,
 *     appearance = PayCrossAppearance(
 *         light = PayCrossColors(brand = 0xFF1E88E5.toInt()),
 *         dark = PayCrossColors(brand = 0xFF64B5F6.toInt()),
 *         shapes = PayCrossShapes(cornerRadius = 16f, buttonCornerRadius = 28f)
 *     )
 * )
 * ```
 */
data class PayCrossAppearance(
    val light: PayCrossColors? = null,
    val dark: PayCrossColors? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val shapes: PayCrossShapes? = null,
    val primaryButton: PayCrossPrimaryButton? = null,
    val typography: PayCrossTypography? = null
) {
    companion object {
        /**
         * Sets one brand colour for both light and dark mode, with platform defaults for everything else.
         */
        @JvmStatic
        fun brand(@ColorInt color: Int): PayCrossAppearance = PayCrossAppearance(
            light = PayCrossColors(brand = color),
            dark = PayCrossColors(brand = color)
        )
    }
}
