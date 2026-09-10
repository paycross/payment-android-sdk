package com.paycross.sdk.internal.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import org.junit.Assert.assertEquals
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
    fun anUnsetSelectDrawsItsPromptWithNothingTappedFirst() {
        renderGroups(sessionLocale = "fr")

        // No interaction before the assertion, which is the whole point. Material
        // paints a placeholder only over a field that is empty AND focused, and a
        // select can never be both — tapping one opens the picker. So the prompt
        // is the field's own displayed text until an option replaces it.
        compose.onNodeWithTag(TestTags.field("billing_address", "country"))
            .assertTextContains("Sélectionnez un pays...")
    }

    @Test
    fun theSelectsPromptIsDrawnInWhicheverLanguageTheSheetChose() {
        renderGroups(sessionLocale = "en")

        compose.onNodeWithTag(TestTags.field("billing_address", "country"))
            .assertTextContains("Select a country...")
    }

    @Test
    fun openingThePickerWritesNothingAndPickingWritesTheValue() {
        val changes = mutableListOf<Triple<String, String, String>>()
        renderGroups(sessionLocale = "en", onValueChange = { g, f, v -> changes += Triple(g, f, v) })

        // Opening the picker is not answering it: the prompt sits in the value
        // slot the whole time and the slot is all it is.
        compose.onNodeWithTag(TestTags.field("billing_address", "country")).performClick()
        compose.onNodeWithText("Latvia").assertIsDisplayed()
        assertEquals(emptyList<Triple<String, String, String>>(), changes)

        compose.onNodeWithText("Latvia").performClick()

        // And what a pick writes is the option's wire value, never the label
        // drawn over it — which is the half of this that can go wrong now that
        // a label occupies the same slot a prompt did a moment ago.
        assertEquals(listOf(Triple("billing_address", "country", "LV")), changes)
    }

    @Test
    fun aPromptIsNotTheSelectsName() {
        renderGroups(sessionLocale = "en")

        // The prompt is drawn where the answer will be and is still not the name
        // a screen reader reads, which stays the field's own label.
        assertEquals(
            listOf("Country"),
            compose.onNodeWithTag(TestTags.field("billing_address", "country"))
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.ContentDescription)
        )
    }

    @Test
    fun aSelectWithAChoiceDrawsTheChoiceRatherThanThePrompt() {
        renderGroups(
            sessionLocale = "fr",
            values = mapOf("billing_address" to mapOf("country" to "LV"))
        )

        val country = compose.onNodeWithTag(TestTags.field("billing_address", "country"))
        country.assertTextContains("Lettonie")
        compose.onNodeWithText("Sélectionnez un pays...", useUnmergedTree = true).assertDoesNotExist()
    }

    private fun renderGroups(
        sessionLocale: String,
        values: Map<String, Map<String, String>> = emptyMap(),
        onValueChange: (String, String, String) -> Unit = { _, _, _ -> }
    ) {
        compose.setContent {
            PayCrossLocalization(sessionLocale = sessionLocale, merchantLocale = null) {
                FieldGroupsSection(
                    groups = groups,
                    values = values,
                    errors = emptyMap(),
                    optedInGroups = emptySet(),
                    onOptInChange = { _, _ -> },
                    onValueChange = onValueChange
                )
            }
        }
    }

    private fun fieldGroups(json: String): List<FieldGroup> =
        Gson().fromJson(json.trimIndent(), SessionResponse::class.java).data!!.fieldGroups!!
}
