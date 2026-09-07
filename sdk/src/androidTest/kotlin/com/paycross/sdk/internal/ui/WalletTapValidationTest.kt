package com.paycross.sdk.internal.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.FieldDefinition
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.SessionData
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Which validation a tap reveals.
 *
 * The sheet has two validated surfaces — the card form and the server-driven
 * field groups — and they are revealed by different taps. A wallet tap never
 * submits the card form, so an empty card form is not an error the shopper has
 * made; the field groups are validated, because the wallet branch submits them.
 * Pay reveals both. That was #58: one flag drove both surfaces, so tapping
 * Google Pay painted four untouched card fields red.
 */
@RunWith(AndroidJUnit4::class)
class WalletTapValidationTest {

    @get:Rule
    val compose = createComposeRule()

    private val cardFieldTags = listOf(
        TestTags.CARD_NUMBER,
        TestTags.EXPIRY,
        TestTags.CVV,
        TestTags.CARDHOLDER_NAME
    )

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
    fun aWalletTapValidatesTheFieldGroupsAndLeavesTheCardFormPristine() {
        renderSheet()

        compose.onNodeWithTag(TestTags.WALLET_BUTTON).performClick()

        cardFieldTags.forEach { tag ->
            compose.onNodeWithTag(tag).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Error)) {
                "$tag is in the error state after a wallet tap, having never been typed in"
            }
        }
        compose.onNodeWithTag(TestTags.fieldError("billing", "city"), useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun payAfterAWalletTapStillRaisesTheCardErrors() {
        renderSheet()

        compose.onNodeWithTag(TestTags.WALLET_BUTTON).performClick()
        compose.onNodeWithTag(TestTags.PAY_BUTTON).performClick()

        cardFieldTags.forEach { tag ->
            compose.onNodeWithTag(tag).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Error)) {
                "$tag is not in the error state after Pay, with nothing typed in it"
            }
        }
        compose.onNodeWithTag(TestTags.fieldError("billing", "city"), useUnmergedTree = true)
            .assertExists()
    }

    /**
     * Guarded the way the other wallet tests guard the same path: Google's
     * PayButton only draws on a device with Play services, and there is no tap
     * to make without it.
     */
    private fun renderSheet() {
        val gmsAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeTrue(gmsAvailable == ConnectionResult.SUCCESS)

        compose.setContent {
            CardFormScreen(
                claims = claims,
                sessionData = sessionData(),
                googlePayAvailable = true,
                onSubmit = { _, _ -> }
            )
        }
    }

    /** No saved cards, so the card form is the new-card form, and one required
     *  field group the shopper has not filled in. */
    private fun sessionData() = SessionData(
        locale = null,
        returnUrl = null,
        successUrl = null,
        fieldGroups = listOf(
            FieldGroup(
                key = "billing",
                label = "Billing",
                fields = listOf(
                    FieldDefinition(
                        name = "city",
                        type = "text",
                        label = "City",
                        placeholder = null,
                        required = true,
                        readonly = null,
                        value = null,
                        condition = null,
                        options = null,
                        validation = null
                    )
                )
            )
        ),
        merchantCountry = null,
        saveCardConfig = null,
        savedCards = emptyList(),
        savedCardsConfig = null,
        wallets = null,
        accountFunding = null,
        googlePay = null
    )
}
