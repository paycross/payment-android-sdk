package com.paycross.sdk.internal.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.paycross.sdk.PayCrossAppearance
import com.paycross.sdk.PayCrossColors
import com.paycross.sdk.PayCrossPrimaryButton
import com.paycross.sdk.PayCrossShapes
import com.paycross.sdk.PayCrossTypography
import com.paycross.sdk.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val CODE_BRAND = Color(0xFF00A86B)
private const val SERVER_BRAND = "#1E88E5"

class AppearanceResolverTest {

    @Test
    fun `code brand beats the server brand`() {
        val resolved = AppearanceResolver.resolve(
            appearance = PayCrossAppearance.brand(CODE_BRAND.toArgb()),
            serverBrandHex = SERVER_BRAND,
            systemDark = false
        )
        assertEquals(CODE_BRAND, resolved.colorScheme.primary)
    }

    @Test
    fun `the server brand beats the platform default`() {
        val resolved = AppearanceResolver.resolve(null, SERVER_BRAND, systemDark = false)
        assertEquals(Color(0xFF1E88E5), resolved.colorScheme.primary)
    }

    @Test
    fun `the platform default stands when nothing is set`() {
        val resolved = AppearanceResolver.resolve(null, null, systemDark = false)
        assertEquals(lightColorScheme().primary, resolved.colorScheme.primary)
    }

    @Test
    fun `the server brand applies in dark mode too`() {
        val resolved = AppearanceResolver.resolve(null, SERVER_BRAND, systemDark = true)
        assertEquals(Color(0xFF1E88E5), resolved.colorScheme.primary)
    }

    @Test
    fun `each mode reads its own palette`() {
        val appearance = PayCrossAppearance(
            light = PayCrossColors(brand = Color.Red.toArgb()),
            dark = PayCrossColors(brand = Color.Blue.toArgb())
        )
        assertEquals(Color.Red, AppearanceResolver.resolve(appearance, null, systemDark = false).colorScheme.primary)
        assertEquals(Color.Blue, AppearanceResolver.resolve(appearance, null, systemDark = true).colorScheme.primary)
    }

    @Test
    fun `a light brand derives a dark onBrand`() {
        val resolved = AppearanceResolver.resolve(
            PayCrossAppearance.brand(Color(0xFFFFF176).toArgb()), null, systemDark = false
        )
        assertEquals(Color.Black, resolved.colorScheme.onPrimary)
    }

    @Test
    fun `a dark brand derives a light onBrand`() {
        val resolved = AppearanceResolver.resolve(
            PayCrossAppearance.brand(Color(0xFF0D47A1).toArgb()), null, systemDark = false
        )
        assertEquals(Color.White, resolved.colorScheme.onPrimary)
    }

    @Test
    fun `a mid grey brand derives a dark onBrand`() {
        assertEquals(Color.Black, AppearanceResolver.onBrandColor(Color(0xFF808080)))
    }

    @Test
    fun `an explicit onBrand is not derived over`() {
        val appearance = PayCrossAppearance(
            light = PayCrossColors(brand = Color(0xFFFFF176).toArgb(), onBrand = Color.Magenta.toArgb())
        )
        val resolved = AppearanceResolver.resolve(appearance, null, systemDark = false)
        assertEquals(Color.Magenta, resolved.colorScheme.onPrimary)
    }

    @Test
    fun `a server brand derives its own onBrand`() {
        val resolved = AppearanceResolver.resolve(null, "#FFF176", systemDark = true)
        assertEquals(Color.Black, resolved.colorScheme.onPrimary)
    }

    @Test
    fun `surface falls back to the platform scheme per mode`() {
        assertEquals(
            lightColorScheme().surface,
            AppearanceResolver.resolve(null, null, systemDark = false).colorScheme.surface
        )
        assertEquals(
            darkColorScheme().surface,
            AppearanceResolver.resolve(null, null, systemDark = true).colorScheme.surface
        )
    }

    @Test
    fun `component falls back to Material rather than to a scheme colour`() {
        assertNull(AppearanceResolver.resolve(null, null, systemDark = false).component)
        assertNull(AppearanceResolver.resolve(null, null, systemDark = false).placeholder)
    }

    @Test
    fun `every colour role reaches the slot it owns`() {
        val appearance = PayCrossAppearance(
            light = PayCrossColors(
                surface = Color.Red.toArgb(),
                component = Color.Green.toArgb(),
                componentBorder = Color.Blue.toArgb(),
                text = Color.Cyan.toArgb(),
                textSecondary = Color.Magenta.toArgb(),
                placeholder = Color.Yellow.toArgb(),
                icon = Color.LightGray.toArgb(),
                error = Color.DarkGray.toArgb()
            )
        )
        val resolved = AppearanceResolver.resolve(appearance, null, systemDark = false)

        assertEquals(Color.Red, resolved.colorScheme.surface)
        assertEquals(Color.Red, resolved.colorScheme.background)
        assertEquals(Color.Green, resolved.component)
        assertEquals(Color.Green, resolved.colorScheme.surfaceContainerHigh)
        assertEquals(Color.Blue, resolved.colorScheme.outline)
        assertEquals(Color.Cyan, resolved.colorScheme.onSurface)
        assertEquals(Color.Cyan, resolved.colorScheme.onBackground)
        assertEquals(Color.Magenta, resolved.colorScheme.onSurfaceVariant)
        assertEquals(Color.Yellow, resolved.placeholder)
        assertEquals(Color.LightGray, resolved.icon)
        assertEquals(Color.DarkGray, resolved.colorScheme.error)
    }

    @Test
    fun `an unset icon follows the secondary text colour`() {
        val appearance = PayCrossAppearance(light = PayCrossColors(textSecondary = Color.Magenta.toArgb()))
        assertEquals(Color.Magenta, AppearanceResolver.resolve(appearance, null, systemDark = false).icon)
        assertEquals(
            lightColorScheme().onSurfaceVariant,
            AppearanceResolver.resolve(null, null, systemDark = false).icon
        )
    }

    @Test
    fun `a pinned mode overrides the system`() {
        assertTrue(
            AppearanceResolver.resolve(
                PayCrossAppearance(themeMode = ThemeMode.DARK), null, systemDark = false
            ).dark
        )
        assertFalse(
            AppearanceResolver.resolve(
                PayCrossAppearance(themeMode = ThemeMode.LIGHT), null, systemDark = true
            ).dark
        )
    }

    @Test
    fun `system mode follows the device`() {
        assertTrue(AppearanceResolver.resolve(null, null, systemDark = true).dark)
        assertFalse(AppearanceResolver.resolve(null, null, systemDark = false).dark)
        assertTrue(
            AppearanceResolver.resolve(
                PayCrossAppearance(themeMode = ThemeMode.SYSTEM), null, systemDark = true
            ).dark
        )
    }

    @Test
    fun `a pinned mode picks that mode's palette and scheme`() {
        val appearance = PayCrossAppearance(
            light = PayCrossColors(brand = Color.Red.toArgb()),
            dark = PayCrossColors(brand = Color.Blue.toArgb()),
            themeMode = ThemeMode.DARK
        )
        val resolved = AppearanceResolver.resolve(appearance, null, systemDark = false)

        assertEquals(Color.Blue, resolved.colorScheme.primary)
        assertEquals(darkColorScheme().surface, resolved.colorScheme.surface)
    }

    @Test
    fun `the size scale factor is clamped to the supported range`() {
        assertEquals(0.8f, scaleOf(0.1f), 0f)
        assertEquals(1.3f, scaleOf(4f), 0f)
        assertEquals(1.15f, scaleOf(1.15f), 0f)
        assertEquals(1f, scaleOf(null), 0f)
        assertEquals(1f, AppearanceResolver.resolve(null, null, systemDark = false).sizeScaleFactor, 0f)
    }

    @Test
    fun `shapes cross as dp and default to null`() {
        val appearance = PayCrossAppearance(
            shapes = PayCrossShapes(cornerRadius = 16f, buttonCornerRadius = 28f, borderWidth = 2f)
        )
        val shapes = AppearanceResolver.resolve(appearance, null, systemDark = false).shapes

        assertEquals(16.dp, shapes.cornerRadius)
        assertEquals(28.dp, shapes.buttonCornerRadius)
        assertEquals(2.dp, shapes.borderWidth)

        val none = AppearanceResolver.resolve(null, null, systemDark = false).shapes
        assertNull(none.cornerRadius)
        assertNull(none.buttonCornerRadius)
        assertNull(none.borderWidth)
    }

    @Test
    fun `an unset button radius falls back to the corner radius`() {
        val appearance = PayCrossAppearance(shapes = PayCrossShapes(cornerRadius = 16f))
        assertEquals(16.dp, AppearanceResolver.resolve(appearance, null, systemDark = false).shapes.buttonCornerRadius)
    }

    @Test
    fun `the primary button overrides cross and fall back to null`() {
        val appearance = PayCrossAppearance(
            primaryButton = PayCrossPrimaryButton(
                background = Color.Red.toArgb(),
                textColor = Color.Green.toArgb(),
                disabledBackground = Color.Blue.toArgb(),
                disabledTextColor = Color.Cyan.toArgb(),
                cornerRadius = 20f,
                height = 64f
            )
        )
        val button = AppearanceResolver.resolve(appearance, null, systemDark = false).primaryButton

        assertEquals(Color.Red, button.background)
        assertEquals(Color.Green, button.textColor)
        assertEquals(Color.Blue, button.disabledBackground)
        assertEquals(Color.Cyan, button.disabledTextColor)
        assertEquals(20.dp, button.cornerRadius)
        assertEquals(64.dp, button.height)

        val none = AppearanceResolver.resolve(null, null, systemDark = false).primaryButton
        assertNull(none.background)
        assertNull(none.cornerRadius)
        assertNull(none.height)
    }

    @Test
    fun `an unset radius on the button falls back to the shapes`() {
        val appearance = PayCrossAppearance(shapes = PayCrossShapes(buttonCornerRadius = 28f))
        assertEquals(
            28.dp,
            AppearanceResolver.resolve(appearance, null, systemDark = false).primaryButton.cornerRadius
        )
    }

    @Test
    fun `a size scale that is not a number is ignored`() {
        // Ignored rather than clamped: an infinite scale is a mistake, not a
        // request for the largest supported one. A finite value out of range is
        // a request, so it still clamps.
        assertEquals(1f, scaleOf(Float.NaN), 0f)
        assertEquals(1f, scaleOf(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, scaleOf(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(0.8f, scaleOf(-2f), 0f)
    }

    @Test
    fun `a radius or thickness that is not a dimension is ignored`() {
        // NaN survives both coerceIn and the Dp constructor, and Dp.roundToPx
        // throws on it rather than rounding, which the Google Pay button's
        // radius reaches.
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f).forEach { bad ->
            val shapes = AppearanceResolver.resolve(
                PayCrossAppearance(
                    shapes = PayCrossShapes(
                        cornerRadius = bad,
                        buttonCornerRadius = bad,
                        borderWidth = bad
                    )
                ),
                null,
                systemDark = false
            ).shapes

            assertNull("cornerRadius accepted $bad", shapes.cornerRadius)
            assertNull("buttonCornerRadius accepted $bad", shapes.buttonCornerRadius)
            assertNull("borderWidth accepted $bad", shapes.borderWidth)
        }
    }

    @Test
    fun `a button radius or height that is not a dimension is ignored`() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, -1f).forEach { bad ->
            val button = AppearanceResolver.resolve(
                PayCrossAppearance(primaryButton = PayCrossPrimaryButton(cornerRadius = bad, height = bad)),
                null,
                systemDark = false
            ).primaryButton

            assertNull("button cornerRadius accepted $bad", button.cornerRadius)
            assertNull("button height accepted $bad", button.height)
        }
    }

    @Test
    fun `a bad button radius still falls back to the shapes`() {
        val appearance = PayCrossAppearance(
            shapes = PayCrossShapes(buttonCornerRadius = 28f),
            primaryButton = PayCrossPrimaryButton(cornerRadius = Float.NaN)
        )
        assertEquals(
            28.dp,
            AppearanceResolver.resolve(appearance, null, systemDark = false).primaryButton.cornerRadius
        )
    }

    @Test
    fun `zero is a dimension`() {
        val appearance = PayCrossAppearance(
            shapes = PayCrossShapes(cornerRadius = 0f, borderWidth = 0f)
        )
        val shapes = AppearanceResolver.resolve(appearance, null, systemDark = false).shapes

        assertEquals(0.dp, shapes.cornerRadius)
        assertEquals(0.dp, shapes.borderWidth)
    }

    @Test
    fun `a button background derives its own label colour`() {
        // Otherwise the label keeps the colour the brand derived, which on a
        // white button is white on white.
        val appearance = PayCrossAppearance(
            primaryButton = PayCrossPrimaryButton(background = Color.White.toArgb())
        )
        val button = AppearanceResolver.resolve(appearance, null, systemDark = false).primaryButton

        assertEquals(Color.White, button.background)
        assertEquals(Color.Black, button.textColor)
    }

    @Test
    fun `an explicit button label colour is kept`() {
        val appearance = PayCrossAppearance(
            primaryButton = PayCrossPrimaryButton(
                background = Color.White.toArgb(),
                textColor = Color.Magenta.toArgb()
            )
        )
        assertEquals(
            Color.Magenta,
            AppearanceResolver.resolve(appearance, null, systemDark = false).primaryButton.textColor
        )
    }

    @Test
    fun `a button with no background derives no label colour`() {
        assertNull(
            AppearanceResolver.resolve(null, null, systemDark = false).primaryButton.textColor
        )
    }

    @Test
    fun `an unreadable sheet warns`() {
        val appearance = PayCrossAppearance(
            light = PayCrossColors(surface = Color(0xFF767676).toArgb(), text = Color(0xFF8A8A8A).toArgb())
        )
        val warnings = AppearanceResolver.contrastWarnings(
            AppearanceResolver.resolve(appearance, null, systemDark = false)
        )
        assertEquals(1, warnings.size)
        assertTrue(warnings.single(), warnings.single().contains("sheet"))
    }

    @Test
    fun `a merchant surface with the platform text colour is checked too`() {
        // Setting only the surface is the common case, and it is exactly the one
        // that can drop the platform's text colour onto an unreadable ground.
        val appearance = PayCrossAppearance(light = PayCrossColors(surface = Color(0xFF1C1B1F).toArgb()))
        assertEquals(
            1,
            AppearanceResolver.contrastWarnings(
                AppearanceResolver.resolve(appearance, null, systemDark = false)
            ).size
        )
    }

    @Test
    fun `six digit hex parses`() {
        assertEquals(Color(0xFF1E88E5), AppearanceResolver.parseHexColor("#1E88E5"))
    }

    @Test
    fun `three digit hex expands`() {
        assertEquals(Color(0xFF00AAFF), AppearanceResolver.parseHexColor("#0AF"))
    }

    @Test
    fun `hex parsing tolerates case and surrounding whitespace`() {
        assertEquals(Color(0xFF1E88E5), AppearanceResolver.parseHexColor(" #1e88e5 "))
    }

    @Test
    fun `the hash is required`() {
        assertNull(AppearanceResolver.parseHexColor("1E88E5"))
        assertNull(AppearanceResolver.parseHexColor("0AF"))
    }

    @Test
    fun `eight digits are refused`() {
        // Core normalises the field to #RRGGBB, and alpha has no meaning on a
        // colour the sheet fills a button with.
        assertNull(AppearanceResolver.parseHexColor("#1E88E5FF"))
        assertNull(AppearanceResolver.parseHexColor("#FF1E88E5"))
    }

    @Test
    fun `invalid hex parses to null`() {
        assertNull(AppearanceResolver.parseHexColor(null))
        assertNull(AppearanceResolver.parseHexColor(""))
        assertNull(AppearanceResolver.parseHexColor("#"))
        assertNull(AppearanceResolver.parseHexColor("#12345"))
        assertNull(AppearanceResolver.parseHexColor("#GGGGGG"))
        assertNull(AppearanceResolver.parseHexColor("rebeccapurple"))
        // isDigit is Unicode-wide and Long.parseLong reads these as 3, so this
        // used to resolve to a colour nobody asked for.
        assertNull(AppearanceResolver.parseHexColor("#٣٣٣"))
    }

    @Test
    fun `an unparseable server brand leaves the platform default alone`() {
        val resolved = AppearanceResolver.resolve(null, "not-a-colour", systemDark = false)
        assertEquals(lightColorScheme().primary, resolved.colorScheme.primary)
    }

    @Test
    fun `a brand and onBrand pair under four and a half to one warns`() {
        val appearance = PayCrossAppearance(
            light = PayCrossColors(brand = Color(0xFF767676).toArgb(), onBrand = Color(0xFF8A8A8A).toArgb())
        )
        val warnings = AppearanceResolver.contrastWarnings(
            AppearanceResolver.resolve(appearance, null, systemDark = false)
        )
        assertEquals(1, warnings.size)
        assertTrue(warnings.single(), warnings.single().contains("brand"))
        // The content colour, then the one behind it, in the order they are read.
        assertTrue(warnings.single(), warnings.single().contains("#8A8A8A on #767676"))
    }

    @Test
    fun `a readable pair does not warn`() {
        val appearance = PayCrossAppearance(
            light = PayCrossColors(brand = Color(0xFF0D47A1).toArgb(), onBrand = Color.White.toArgb())
        )
        assertTrue(
            AppearanceResolver.contrastWarnings(
                AppearanceResolver.resolve(appearance, null, systemDark = false)
            ).isEmpty()
        )
    }

    @Test
    fun `a derived onBrand never warns`() {
        listOf(0xFFFFF176, 0xFF0D47A1, 0xFF808080, 0xFF767676).forEach { brand ->
            val resolved = AppearanceResolver.resolve(
                PayCrossAppearance.brand(Color(brand).toArgb()), null, systemDark = false
            )
            assertTrue(
                "brand ${brand.toString(16)} warned",
                AppearanceResolver.contrastWarnings(resolved).isEmpty()
            )
        }
    }

    @Test
    fun `an unreadable button pair warns`() {
        val appearance = PayCrossAppearance(
            primaryButton = PayCrossPrimaryButton(
                background = Color(0xFF767676).toArgb(),
                textColor = Color(0xFF8A8A8A).toArgb()
            )
        )
        val warnings = AppearanceResolver.contrastWarnings(
            AppearanceResolver.resolve(appearance, null, systemDark = false)
        )
        assertEquals(1, warnings.size)
        assertTrue(warnings.single(), warnings.single().contains("button"))
    }

    @Test
    fun `the default appearance never warns`() {
        listOf(true, false).forEach { dark ->
            assertTrue(
                AppearanceResolver.contrastWarnings(
                    AppearanceResolver.resolve(null, null, systemDark = dark)
                ).isEmpty()
            )
        }
    }

    private fun scaleOf(factor: Float?): Float = AppearanceResolver.resolve(
        PayCrossAppearance(typography = PayCrossTypography(sizeScaleFactor = factor)),
        null,
        systemDark = false
    ).sizeScaleFactor
}
