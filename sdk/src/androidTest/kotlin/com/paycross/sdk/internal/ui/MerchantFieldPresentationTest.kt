package com.paycross.sdk.internal.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.ui.components.FieldGroupsSection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What a merchant-configured field tells a shopper before they tap Pay: which
 * fields they have to fill, and what a select expects when nothing is chosen.
 *
 * Both were only knowable by submitting. The sheet computed `required` for the
 * validator and drew a required field exactly like an optional one, and a
 * select's placeholder — the one string in a whole field-group configuration
 * that is genuinely translated rather than a format example — never reached the
 * field at all.
 */
@RunWith(AndroidJUnit4::class)
class MerchantFieldPresentationTest {

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
                "key": "customer_info",
                "label": "Your details",
                "labels": {"en": "Your details", "fr": "Vos coordonnées"},
                "fields": [
                  {
                    "name": "email",
                    "type": "email",
                    "label": "Email address",
                    "labels": {"en": "Email address", "fr": "Adresse e-mail"},
                    "required": true
                  },
                  {
                    "name": "phone",
                    "type": "tel",
                    "label": "Phone number",
                    "labels": {"en": "Phone number", "fr": "Numéro de téléphone"},
                    "required": false
                  }
                ]
              },
              {
                "key": "billing_address",
                "label": "Billing address",
                "labels": {"en": "Billing address", "fr": "Adresse de facturation"},
                "fields": [
                  {
                    "name": "country",
                    "type": "select",
                    "label": "Country",
                    "labels": {"en": "Country", "fr": "Pays"},
                    "placeholder": "Select a country...",
                    "placeholders": {"en": "Select a country...", "fr": "Sélectionnez un pays..."},
                    "required": true,
                    "options": [
                      {"value": "FR", "label": "France", "labels": {"en": "France", "fr": "France"}},
                      {"value": "LV", "label": "Latvia", "labels": {"en": "Latvia", "fr": "Lettonie"}}
                    ]
                  },
                  {
                    "name": "state",
                    "type": "text",
                    "label": "State",
                    "labels": {"en": "State", "fr": "État"},
                    "required": false,
                    "condition": {"when": "country", "in": ["US"], "display": "required", "default": "optional"}
                  }
                ]
              }
            ]
          }
        }
        """
    )

    @Test
    fun aRequiredFieldCarriesTheMarkerAndAnOptionalOneDoesNot() {
        renderGroups(sessionLocale = "fr")

        // Unmerged: a labelled field merges its descendants, so the label is a
        // node of its own only before the merge.
        compose.onNodeWithText("Adresse e-mail *", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Pays *", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Numéro de téléphone", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theMarkerSitsOnWhichEverLabelTheSheetsLanguageChose() {
        renderGroups(sessionLocale = "en")

        compose.onNodeWithText("Email address *", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Phone number", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aConditionallyRequiredFieldGainsTheMarkerWhenItsConditionIsMet() {
        // The marker follows the state the validator computes, not the `required`
        // flag on the wire — which is false for this field either way.
        renderGroups(sessionLocale = "en", values = mapOf("billing_address" to mapOf("country" to "US")))

        compose.onNodeWithText("State *", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theSameFieldKeepsAnUnmarkedLabelWhileItsConditionIsUnmet() {
        renderGroups(sessionLocale = "en", values = mapOf("billing_address" to mapOf("country" to "FR")))

        compose.onNodeWithText("State", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anUnsetSelectDrawsItsPlaceholderInTheSheetsLanguage() {
        renderGroups(sessionLocale = "fr")

        // Material floats the label out of the box before it draws a placeholder
        // underneath, and only a focused field does that — the same state the
        // text fields' own placeholders appear in.
        compose.onNodeWithTag(TestTags.field("billing_address", "country")).performClick()

        compose.onNodeWithText("Sélectionnez un pays...", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aSelectWithAChoiceDrawsTheChoiceRatherThanThePlaceholder() {
        renderGroups(
            sessionLocale = "fr",
            values = mapOf("billing_address" to mapOf("country" to "LV"))
        )

        val country = compose.onNodeWithTag(TestTags.field("billing_address", "country"))
        country.assertTextContains("Lettonie")

        // Focused, the state a placeholder would be drawn in if one were passed.
        country.performClick()
        compose.onNodeWithText("Sélectionnez un pays...", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun renderGroups(
        sessionLocale: String,
        values: Map<String, Map<String, String>> = emptyMap()
    ) {
        compose.setContent {
            PayCrossLocalization(sessionLocale = sessionLocale, merchantLocale = null) {
                FieldGroupsSection(
                    groups = groups,
                    values = values,
                    errors = emptyMap(),
                    onValueChange = { _, _, _ -> }
                )
            }
        }
    }

    private fun fieldGroups(json: String): List<FieldGroup> =
        Gson().fromJson(json.trimIndent(), SessionResponse::class.java).data!!.fieldGroups!!
}
