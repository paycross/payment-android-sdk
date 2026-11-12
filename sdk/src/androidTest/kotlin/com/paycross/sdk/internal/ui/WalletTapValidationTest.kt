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
