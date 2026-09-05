package com.paycross.sdk.internal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        renderPayButton(isLoading = false)
        compose.onNodeWithText("Pay €1.00").assertIsDisplayed()

        assertContentIsDark("label")
    }

    @Test
    fun lightBrandPaintsADarkSpinner() {
        // The indeterminate spinner animates forever, so an auto-advancing test
        // clock never reports idle and captureToImage would time out waiting.
        compose.mainClock.autoAdvance = false
        renderPayButton(isLoading = true)
        compose.mainClock.advanceTimeBy(400)

        assertContentIsDark("spinner")
    }

    private fun renderPayButton(isLoading: Boolean) {
        compose.setContent {
            PayCrossTheme(brand = null) {
                Box(Modifier.testTag(PAY_BUTTON_TAG)) {
                    PayButton(
                        amount = "€1.00",
                        isLoading = isLoading,
                        brandColor = lightBrand,
                        onClick = {}
                    )
                }
            }
        }
    }

    /**
     * Reads a central column band of the button, clear of the rounded corners and
     * of the antialiased outer edge, so neither assertion can be satisfied by
     * what sits behind the button rather than by what the button painted itself.
     */
    private fun assertContentIsDark(what: String) {
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

        assertTrue("expected a dark $what on a light brand, darkest pixel was $darkest", darkest < 0.05f)
        assertTrue("expected no white $what pixels, found $nearWhite", nearWhite == 0)
    }
}
