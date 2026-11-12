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
        compose.mainClock.autoAdvance = false
        renderPayButton(isLoading = true, appearance = PayCrossAppearance.brand(lightBrand.toArgb()))
        compose.mainClock.advanceTimeBy(400)

        assertContentIsDarkerThanTheBrand("spinner")
    }

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
