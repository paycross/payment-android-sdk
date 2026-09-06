package com.paycross.sdk.internal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.SavedCard
import com.paycross.sdk.internal.api.models.SavedCardsConfig
import com.paycross.sdk.internal.api.models.SessionData
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the sheet does when the shopper turns their text size up.
 *
 * Three times the default, not two: Android's accessibility font sizes stop at
 * 2×, but display size multiplies on top of them, and a control pinned to a
 * fixed size crops its label rather than growing. Both sizes here were fixed
 * before this test existed.
 */
@RunWith(AndroidJUnit4::class)
class FontScaleTest {

    @get:Rule
    val compose = createComposeRule()

    private val claims = JwtClaims(
        sessionId = "session-123",
        merchantId = "merchant-456",
        customerId = "customer-1",
        brandingId = null,
        amount = 1234,
        currency = "EUR",
        expiresAt = null
    )

    private val visa = SavedCard(
        uuid = "card-1",
        maskedPan = "453201******0366",
        cardBrand = "visa",
        expireMonth = "12",
        expireYear = "2030",
        cardholderName = "JOHN DOE"
    )

    @Before
    fun setUp() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
    }

    @Test
    fun thePayButtonGrowsPastItsRestingHeight() {
        compose.setContent {
            AtThreeTimesTheTextSize {
                PayButton(amount = "€12.34", isLoading = false, onClick = {})
            }
        }

        val height = compose.onNodeWithTag(TestTags.PAY_BUTTON).fetchSemanticsNode().size.height
        val resting = with(compose.density) { 56.dp.roundToPx() }
        assertTrue(
            "Pay button was $height px, still pinned to its resting $resting px",
            height > resting
        )
    }

    @Test
    fun theSavedCardCvvBoxGrowsPastItsFloor() {
        compose.setContent {
            AtThreeTimesTheTextSize {
                CardFormScreen(
                    claims = claims,
                    sessionData = sessionData(),
                    selectedSavedCardUuid = "card-1",
                    onSubmit = { _, _ -> }
                )
            }
        }

        val width = compose.onNodeWithTag(TestTags.CVV).fetchSemanticsNode().size.width
        val floor = with(compose.density) { 100.dp.roundToPx() }
        // A fixed width(100.dp), which is what this replaced, would measure
        // exactly the floor at every text size.
        assertTrue("CVV box was $width px, still pinned to $floor px", width > floor)
    }

    @Test
    fun theSavedCardCvvBoxSizesToItsContentRatherThanMaterialsDefault() {
        compose.setContent {
            CardFormScreen(
                claims = claims,
                sessionData = sessionData(),
                selectedSavedCardUuid = "card-1",
                onSubmit = { _, _ -> }
            )
        }

        // Between the two: never under the floor, and never Material's 280dp
        // default width, which is what a field with no width modifier at all
        // would take right across the sheet.
        val width = compose.onNodeWithTag(TestTags.CVV).fetchSemanticsNode().size.width
        val floor = with(compose.density) { 100.dp.roundToPx() }
        val materialDefault = with(compose.density) { 280.dp.roundToPx() }
        assertTrue("CVV box was $width px, under the $floor px floor", width >= floor)
        assertTrue(
            "CVV box was $width px, Material's default $materialDefault px",
            width < materialDefault
        )
    }

    @Composable
    private fun AtThreeTimesTheTextSize(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, fontScale = 3f),
            content = content
        )
    }

    private fun sessionData() = SessionData(
        locale = null,
        returnUrl = null,
        successUrl = null,
        fieldGroups = null,
        merchantCountry = null,
        saveCardConfig = null,
        savedCards = listOf(visa),
        savedCardsConfig = SavedCardsConfig(allowRemoval = false, preselect = true),
        wallets = null,
        accountFunding = null,
        googlePay = null
    )
}
