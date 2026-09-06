package com.paycross.sdk.internal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paycross.sdk.PayCrossAppearance
import com.paycross.sdk.PayCrossColors
import com.paycross.sdk.internal.ui.theme.AppearanceResolver
import com.paycross.sdk.internal.ui.theme.PayCrossTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PAY_BUTTON_TAG = "pay-button-under-test"

/**
 * The payment activity sets FLAG_SECURE, so a device screenshot of this button
 * comes back black and cannot evidence a contrast fix. The rendered pixels are
 * read back here instead.
 */
@RunWith(AndroidJUnit4::class)
class PayButtonContrastTest {

    @get:Rule
    val compose = createComposeRule()

    private val lightBrand = Color(0xFFFFF176)

    @Test
    fun lightBrandPaintsADarkLabel() {
        renderPayButton(isLoading = false, appearance = PayCrossAppearance.brand(lightBrand.toArgb()))
        compose.onNodeWithText("Pay €1.00").assertIsDisplayed()

        assertContentIsDarkerThanTheBrand("label")
    }

    @Test
    fun lightBrandPaintsADarkSpinner() {
        // The indeterminate spinner animates forever, so an auto-advancing test
        // clock never reports idle and captureToImage would time out waiting.
        compose.mainClock.autoAdvance = false
        renderPayButton(isLoading = true, appearance = PayCrossAppearance.brand(lightBrand.toArgb()))
        compose.mainClock.advanceTimeBy(400)

        assertContentIsDarkerThanTheBrand("spinner")
    }

    /**
     * The label colour is derived from the brand's own luminance rather than
     * from the mode, so a light brand a merchant sets for dark mode gets the
     * same dark label it gets in light mode.
     */
    @Test
    fun lightBrandInDarkModeStillPaintsADarkLabel() {
        renderPayButton(
            isLoading = false,
            appearance = PayCrossAppearance.brand(lightBrand.toArgb()),
            dark = true
        )
        compose.onNodeWithText("Pay €1.00").assertIsDisplayed()

        assertContentIsDarkerThanTheBrand("label")
    }

    /**
     * A merchant who asks for white on a light brand gets it. The contrast
     * helper fills in a label colour; it does not overrule one.
     */
    @Test
    fun merchantOnBrandIsNotOverridden() {
        renderPayButton(
            isLoading = false,
            appearance = PayCrossAppearance(
                light = PayCrossColors(brand = lightBrand.toArgb(), onBrand = Color.White.toArgb())
            )
        )
        compose.onNodeWithText("Pay €1.00").assertIsDisplayed()

        val band = measureButtonBand()
        assertTrue(
            "expected no dark label on a merchant-set white onBrand, " +
                "darkest pixel was ${band.darkest}",
            band.darkest >= darkerThanTheBrand()
        )
    }

    private fun renderPayButton(
        isLoading: Boolean,
        appearance: PayCrossAppearance,
        dark: Boolean = false
    ) {
        compose.setContent {
            PayCrossTheme(AppearanceResolver.resolve(appearance, null, systemDark = dark)) {
                Box(Modifier.testTag(PAY_BUTTON_TAG)) {
                    PayButton(
                        amount = "€1.00",
                        isLoading = isLoading,
                        onClick = {}
                    )
                }
            }
        }
    }

    /**
     * Both the spinner's arc and the label's glyph stems are thin antialiased
     * strokes, so every pixel they paint is a blend of the content colour with the
     * brand behind it and, at a low display density, none of them reaches
     * near-black. Both are therefore judged against the brand: black content lands
     * far below the brand's own luminance, while white content would leave the
     * band no darker than the brand.
     */
    private fun darkerThanTheBrand(): Float = lightBrand.luminance() - 0.4f

    private fun assertContentIsDarkerThanTheBrand(what: String) {
        val band = measureButtonBand()
        assertTrue(
            "expected a $what darker than ${darkerThanTheBrand()} on a light brand, " +
                "darkest pixel was ${band.darkest}",
            band.darkest < darkerThanTheBrand()
        )
        assertTrue(
            "expected no white $what pixels, found ${band.nearWhite}",
            band.nearWhite == 0
        )
    }

    private data class Band(val darkest: Float, val nearWhite: Int)

    /**
     * Reads a central column band of the button, clear of the rounded corners and
     * of the antialiased outer edge, so no assertion can be satisfied by what sits
     * behind the button rather than by what the button painted itself.
     */
    private fun measureButtonBand(): Band {
        val pixels = compose.onNodeWithTag(PAY_BUTTON_TAG).captureToImage().toPixelMap()
        var darkest = 1f
        var nearWhite = 0
        for (y in (pixels.height * 0.1f).toInt() until (pixels.height * 0.9f).toInt()) {
            for (x in (pixels.width * 0.3f).toInt() until (pixels.width * 0.7f).toInt()) {
                val luminance = pixels[x, y].luminance()
                if (luminance < darkest) darkest = luminance
                if (luminance > 0.95f) nearWhite++
            }
        }
        return Band(darkest, nearWhite)
    }
}
