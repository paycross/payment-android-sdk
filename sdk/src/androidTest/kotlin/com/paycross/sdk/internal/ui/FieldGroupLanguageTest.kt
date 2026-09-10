package com.paycross.sdk.internal.ui

import androidx.compose.ui.test.assertIsDisplayed
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
 * Proves the merchant's own field copy follows the sheet's language, end to end:
 * a real session blob decoded by the real Gson, through the real locale ladder,
 * onto a rendered field group.
 *
 * The groups are the one part of the sheet whose words the SDK does not own. They
 * used to be drawn in whatever language the session was minted for, so a French
 * shopper on an English-minted session read a French sheet with an English
 * "Email address" in the middle of it. Nothing here re-translates them: the
 * session carries every language the backend has, and the sheet picks the one it
 * already resolved for its own strings.
 */
@RunWith(AndroidJUnit4::class)
class FieldGroupLanguageTest {

    @get:Rule
    val compose = createComposeRule()

    private val translated = fieldGroups(
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
                    "placeholder": "email@example.com",
                    "placeholders": {"en": "email@example.com", "fr": "courriel@exemple.com"},
                    "required": true,
                    "readonly": false,
                    "value": null
                  },
                  {
                    "name": "title",
                    "type": "select",
                    "label": "Title",
                    "labels": {"en": "Title", "fr": "Civilité"},
                    "required": false,
                    "readonly": false,
                    "value": null,
                    "options": [
                      {"value": "mrs", "label": "Mrs", "labels": {"en": "Mrs", "fr": "Madame"}}
                    ]
                  }
                ]
              }
            ]
          }
        }
        """
    )

    private val untranslated = fieldGroups(
        """
        {
          "session_id": "550e8400-e29b-41d4-a716-446655440000",
          "data": {
            "field_groups": [
              {
                "key": "customer_info",
                "label": "Your details",
                "fields": [
                  {"name": "email", "type": "email", "label": "Email address", "required": true}
                ]
              }
            ]
          }
        }
        """
    )

    @Test
    fun aFrenchSheetDrawsTheMerchantsFrenchLabels() {
        renderGroups(sessionLocale = "fr", groups = translated)

        // Unmerged: a labelled field merges its descendants, so the label is a
        // node of its own only before the merge. The marker on the required one
        // is the label's, not another node; MerchantFieldPresentationTest owns it.
        compose.onNodeWithText("Vos coordonnées", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Adresse e-mail *", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Civilité", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun anEnglishSheetDrawsTheMerchantsEnglishLabels() {
        renderGroups(sessionLocale = "en", groups = translated)

        compose.onNodeWithText("Your details", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Email address *", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Title", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aSelectOptionIsDrawnInTheSheetsLanguage() {
        renderGroups(sessionLocale = "fr", groups = translated)

        compose.onNodeWithTag(TestTags.field("customer_info", "title")).performClick()

        compose.onNodeWithText("Madame", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aPlaceholderIsDrawnInTheSheetsLanguage() {
        renderGroups(sessionLocale = "fr", groups = translated)

        // The placeholder of a labelled field appears once the field has focus;
        // before that the label is sitting where it would be drawn.
        compose.onNodeWithTag(TestTags.field("customer_info", "email")).performClick()

        compose.onNodeWithText("courriel@exemple.com", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aSessionWithoutTranslationsKeepsTheLabelsItCameWith() {
        renderGroups(sessionLocale = "fr", groups = untranslated)

        compose.onNodeWithText("Your details", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Email address *", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun renderGroups(sessionLocale: String, groups: List<FieldGroup>) {
        compose.setContent {
            PayCrossLocalization(sessionLocale = sessionLocale, merchantLocale = null) {
                FieldGroupsSection(
                    groups = groups,
                    values = emptyMap(),
                    errors = emptyMap(),
                    optedInGroups = emptySet(),
                    onOptInChange = { _, _ -> },
                    onValueChange = { _, _, _ -> }
                )
            }
        }
    }

    private fun fieldGroups(json: String): List<FieldGroup> =
        Gson().fromJson(json.trimIndent(), SessionResponse::class.java).data!!.fieldGroups!!
}
