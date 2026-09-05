package com.paycross.sdk.internal.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paycross.sdk.internal.ui.theme.PayCrossTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * FLAG_SECURE blocks the screenshots that would normally evidence a contrast
 * fix, so the rendered pixels are read back here instead.
 */
@RunWith(AndroidJUnit4::class)
class PayButtonContrastTest {

    @get:Rule
    val compose = createComposeRule()

    private val lightBrand = Color(0xFFFFF176)

    @Test
    fun lightBrandPaintsDarkLabelAndNoWhiteOnTheButton() {
        compose.setContent {
            PayCrossTheme(brand = null) {
                PayButton(
                    amount = "€1.00",
                    isLoading = false,
                    brandColor = lightBrand,
                    onClick = {}
                )
            }
        }

        compose.onNodeWithText("Pay €1.00").assertIsDisplayed()

        val pixels = compose.onNodeWithText("Pay €1.00").captureToImage().toPixelMap()
        var darkest = 1f
        var lightest = 0f
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                val luminance = pixels[x, y].luminance()
                if (luminance < darkest) darkest = luminance
                if (luminance > lightest) lightest = luminance
            }
        }

        assertTrue("expected a dark label on a light brand, darkest pixel was $darkest", darkest < 0.05f)
        assertTrue("expected nothing lighter than the brand, lightest pixel was $lightest", lightest <= lightBrand.luminance() + 0.01f)
    }
}
