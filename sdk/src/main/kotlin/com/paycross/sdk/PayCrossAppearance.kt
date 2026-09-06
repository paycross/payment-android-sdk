package com.paycross.sdk

import androidx.annotation.ColorInt

/**
 * How the payment sheet chooses between the light and dark palettes.
 *
 * A pinned mode applies to the sheet only. The host app's own theme is never
 * touched.
 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * One colour palette, used twice: once for light, once for dark.
 *
 * Every role is nullable, and null means "keep the platform default" — the
 * Material colour the sheet already draws — so an empty palette changes
 * nothing and a merchant can set one colour without designing a theme.
 *
 * @property brand Pay button fill, selection controls, focus ring.
 * @property onBrand Text and spinner drawn on [brand]. Null derives it from
 *   [brand]'s own luminance, so a light brand gets a dark label.
 * @property surface The sheet's own background.
 * @property component Background of the input fields and of dialogs.
 * @property componentBorder Input borders.
 * @property text Primary text.
 * @property textSecondary Labels, hints and supporting text.
 * @property placeholder Empty-input placeholder text.
 * @property icon The picker's delete glyph and any other icon the sheet draws.
 * @property error Error text and invalid field borders.
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
 * Corner radii and border thickness, in dp. Null keeps Material's value.
 *
 * @property cornerRadius Input fields and the small components Material draws
 *   around them.
 * @property buttonCornerRadius The Pay button and the wallet button. Falls back
 *   to [cornerRadius].
 * @property borderWidth Input border thickness. Material's 1dp unfocused and
 *   2dp focused become this value in both states.
 */
data class PayCrossShapes(
    val cornerRadius: Float? = null,
    val buttonCornerRadius: Float? = null,
    val borderWidth: Float? = null
)

/**
 * Pay button overrides. Each null falls back to the matching palette role, and
 * then to Material.
 *
 * @property background Falls back to [PayCrossColors.brand].
 * @property textColor Falls back to [PayCrossColors.onBrand].
 * @property cornerRadius Falls back to [PayCrossShapes.buttonCornerRadius].
 * @property height In dp. Defaults to 56.
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
 * @property sizeScaleFactor Multiplies every font size in the sheet. It
 *   composes with the device's own font scale rather than replacing it, and is
 *   clamped to 0.8–1.3: past those bounds the sheet either stops being legible
 *   or stops fitting on a small screen. Null leaves sizes alone.
 */
data class PayCrossTypography(
    val sizeScaleFactor: Float? = null
)

/**
 * How the payment sheet looks.
 *
 * Colours resolve per role, in this order: what is set here, then the brand
 * colour the merchant set in the back office, then the platform default. Light
 * and dark are symmetric — both palettes are yours to set, and the sheet picks
 * one by [themeMode].
 *
 * Layout, the card fields' internals, the wallet buttons' own colours and
 * labels, the 3-D Secure page and the error copy are fixed by design.
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
         * One brand colour in both modes and platform defaults for everything
         * else. The whole migration path off the deprecated `brandColor`.
         */
        @JvmStatic
        fun brand(@ColorInt color: Int): PayCrossAppearance = PayCrossAppearance(
            light = PayCrossColors(brand = color),
            dark = PayCrossColors(brand = color)
        )
    }
}
