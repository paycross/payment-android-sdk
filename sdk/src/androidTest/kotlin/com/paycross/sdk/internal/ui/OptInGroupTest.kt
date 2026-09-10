package com.paycross.sdk.internal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.SessionData
import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.ui.components.FieldGroupsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The affordance for a group a shopper is allowed to decline.
 *
 * A merchant can open a group up so the backend takes the payment without it,
 * every field in it configured required or not. The sheet used to enforce all of
 * them anyway, and offered nowhere to say no — a shopper who did not want to give
 * a shipping address had to invent one to get through.
 *
 * The caption is the merchant's own group label in the sheet's language, so the
 * toggle names the thing being declined without the SDK translating a word of it.
 */
@RunWith(AndroidJUnit4::class)
class OptInGroupTest {

    @get:Rule
    val compose = createComposeRule()

    private val groups = fieldGroups(
        """
        {
          "session_id": "550e8400-e29b-41d4-a716-446655440000",
          "data": {
            "locale": "en",
            "field_groups": [
              {
                "key": "billing_address",
                "label": "Billing address",
                "labels": {"en": "Billing address", "fr": "Adresse de facturation"},
                "fields": [
                  {
                    "name": "city",
                    "type": "text",
                    "label": "City",
                    "labels": {"en": "City", "fr": "Ville"},
                    "required": true
                  }
                ]
              },
              {
                "key": "shipping_address",
                "label": "Shipping address",
                "labels": {"en": "Shipping address", "fr": "Adresse de livraison"},
                "opt_in": true,
                "fields": [
                  {
                    "name": "line1",
                    "type": "text",
                    "label": "Address line 1",
                    "labels": {"en": "Address line 1", "fr": "Adresse ligne 1"},
                    "required": true
                  },
                  {
                    "name": "city",
                    "type": "text",
                    "label": "City",
                    "labels": {"en": "City", "fr": "Ville"},
                    "required": true
                  }
                ]
              }
            ]
          }
        }
        """
    )

    @Test
    fun anOptInGroupOffersAToggleThatStartsOffAndIsCaptionedByItsLabel() {
        renderGroups(sessionLocale = "en")

        val toggle = compose.onNodeWithTag(TestTags.groupOptIn("shipping_address"))
        toggle.assertIsOff()
        assertTrue(
            "the toggle is unnamed",
            toggle.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .any { it.text == "Shipping address" }
        )
    }

    @Test
    fun theToggleIsCaptionedInWhicheverLanguageTheSheetChose() {
        renderGroups(sessionLocale = "fr")

        val toggle = compose.onNodeWithTag(TestTags.groupOptIn("shipping_address"))
        assertTrue(
            "the caption is not the merchant's French label",
            toggle.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .any { it.text == "Adresse de livraison" }
        )
    }

    @Test
    fun theGroupsFieldsAreNotDrawnWhileTheToggleIsOff() {
        renderGroups(sessionLocale = "en")

        compose.onNodeWithTag(TestTags.field("shipping_address", "line1")).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.field("shipping_address", "city")).assertDoesNotExist()
    }

    @Test
    fun turningTheToggleOnDrawsTheGroupsFields() {
        renderGroups(sessionLocale = "fr")

        compose.onNodeWithTag(TestTags.groupOptIn("shipping_address")).performClick()

        compose.onNodeWithTag(TestTags.groupOptIn("shipping_address")).assertIsOn()
        compose.onNodeWithTag(TestTags.field("shipping_address", "line1")).assertIsDisplayed()
        // The merchant's own label for a field only reachable once the group is on.
        compose.onNodeWithText("Adresse ligne 1 *", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aGroupWithNoOptInFlagKeepsItsHeadingAndItsFields() {
        renderGroups(sessionLocale = "en")

        compose.onNodeWithTag(TestTags.groupOptIn("billing_address")).assertDoesNotExist()
        compose.onNodeWithText("Billing address", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.field("billing_address", "city")).assertIsDisplayed()
    }

    /**
     * The whole point of the toggle, through the sheet rather than through the
     * validator: a shopper who declines the group pays, and the payment carries
     * no trace of it.
     *
     * The card details are typed rather than prefilled because reaching the
     * submit callback at all is half the assertion — this is the case that used
     * to stop at five required-field errors on a group nobody asked for.
     */
    @Test
    fun aShopperWhoDeclinesTheGroupCanPayAndTheGroupIsNotSubmitted() {
        var submitted: Map<String, Map<String, String>>? = null
        PayCross.init(environment = PayCrossEnvironment.STAGING)

        compose.setContent {
            PayCrossLocalization(sessionLocale = "fr", merchantLocale = null) {
                CardFormScreen(
                    claims = claims,
                    sessionData = sessionData,
                    onSubmit = { _, fieldGroups -> submitted = fieldGroups }
                )
            }
        }

        compose.onNodeWithTag(TestTags.CARD_NUMBER).performTextInput("4532015112830366")
        compose.onNodeWithTag(TestTags.EXPIRY).performTextInput("1230")
        compose.onNodeWithTag(TestTags.CVV).performTextInput("123")
        compose.onNodeWithTag(TestTags.CARDHOLDER_NAME).performTextInput("JOHN DOE")
        compose.onNodeWithTag(TestTags.field("billing_address", "city"))
            .performScrollTo()
            .performTextInput("Riga")

        compose.onNodeWithTag(TestTags.PAY_BUTTON).performClick()

        assertEquals(mapOf("billing_address" to mapOf("city" to "Riga")), submitted)
    }

    private val claims = JwtClaims(
        sessionId = "session-123",
        merchantId = "merchant-456",
        customerId = "customer-1",
        brandingId = null,
        amount = 1234,
        currency = "EUR",
        expiresAt = null
    )

    private val sessionData = SessionData(
        locale = "fr",
        returnUrl = null,
        successUrl = null,
        fieldGroups = groups,
        merchantCountry = null,
        saveCardConfig = null,
        savedCards = null,
        savedCardsConfig = null,
        wallets = null,
        accountFunding = null,
        googlePay = null
    )

    private fun renderGroups(sessionLocale: String) {
        compose.setContent {
            var optedIn by remember { mutableStateOf(emptySet<String>()) }
            PayCrossLocalization(sessionLocale = sessionLocale, merchantLocale = null) {
                FieldGroupsSection(
                    groups = groups,
                    values = emptyMap(),
                    errors = emptyMap(),
                    optedInGroups = optedIn,
                    onOptInChange = { group, on ->
                        optedIn = if (on) optedIn + group else optedIn - group
                    },
                    onValueChange = { _, _, _ -> }
                )
            }
        }
    }

    private fun fieldGroups(json: String): List<FieldGroup> =
        Gson().fromJson(json.trimIndent(), SessionResponse::class.java).data!!.fieldGroups!!
}
