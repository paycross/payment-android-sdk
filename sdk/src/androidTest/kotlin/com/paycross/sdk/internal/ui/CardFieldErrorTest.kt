package com.paycross.sdk.internal.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.R
import com.paycross.sdk.internal.api.JwtClaims
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CardFieldErrorTest {

    @get:Rule
    val compose = createComposeRule()

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val english = targetContext.localizedResources(Locale.ENGLISH)
    private val french = targetContext.localizedResources(Locale.FRENCH)

    private val claims = JwtClaims(
        sessionId = "session-123",
        merchantId = "merchant-456",
        customerId = "customer-1",
        brandingId = null,
        amount = 1234,
        currency = "EUR",
        expiresAt = null
    )

    @Before
    fun setUp() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
    }

    @Test
    fun everyEmptyCardFieldNamesItsOwnProblemInEnglish() {
        payWithAnEmptyForm(sessionLocale = "en")

        assertMessage(TestTags.CARD_NUMBER, english.getString(R.string.paycross_card_number_invalid))
        assertMessage(TestTags.EXPIRY, english.getString(R.string.paycross_expiry_invalid))
        assertMessage(TestTags.CVV, english.getString(R.string.paycross_cvv_invalid))
        assertMessage(
            TestTags.CARDHOLDER_NAME,
            english.getString(R.string.paycross_cardholder_name_invalid)
        )
    }

    @Test
    fun everyEmptyCardFieldNamesItsOwnProblemInFrench() {
        payWithAnEmptyForm(sessionLocale = "fr")

        assertMessage(TestTags.CARD_NUMBER, french.getString(R.string.paycross_card_number_invalid))
        assertMessage(TestTags.EXPIRY, french.getString(R.string.paycross_expiry_invalid))
        assertMessage(TestTags.CVV, french.getString(R.string.paycross_cvv_invalid))
        assertMessage(
            TestTags.CARDHOLDER_NAME,
            french.getString(R.string.paycross_cardholder_name_invalid)
        )
    }

    @Test
    fun theFrenchExpiryMessageIsTheCopySheetsOwnSentence() {
        payWithAnEmptyForm(sessionLocale = "fr")

        assertMessage(TestTags.EXPIRY, "Entrez une date d'expiration valide")
    }

    @Test
    fun aFormNobodyHasSubmittedYetAccusesNothing() {
        renderSheet(sessionLocale = "en")

        val config = compose.onNodeWithTag(TestTags.CARD_NUMBER).fetchSemanticsNode().config
        assertEquals(null, config.getOrNull(SemanticsProperties.Error))
    }

    private fun assertMessage(tag: String, expected: String) {
        val config = compose.onNodeWithTag(tag).fetchSemanticsNode().config

        assertEquals("$tag carries the wrong error", expected, config.getOrNull(SemanticsProperties.Error))
        assertTrue(
            "$tag does not draw the message",
            config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == expected }
        )
    }

    private fun payWithAnEmptyForm(sessionLocale: String) {
        renderSheet(sessionLocale)
        compose.onNodeWithTag(TestTags.PAY_BUTTON).performClick()
    }

    private fun renderSheet(sessionLocale: String) {
        compose.setContent {
            PayCrossLocalization(sessionLocale = sessionLocale, merchantLocale = null) {
                CardFormScreen(claims = claims, sessionData = null, onSubmit = { _, _ -> })
            }
        }
        compose.waitForIdle()
    }
}

