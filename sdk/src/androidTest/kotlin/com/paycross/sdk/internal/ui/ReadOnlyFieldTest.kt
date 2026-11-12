package com.paycross.sdk.internal.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.ui.components.FieldGroupsSection
import com.paycross.sdk.internal.validation.FieldGroupLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadOnlyFieldTest {

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
                "fields": [
                  {
                    "name": "locked",
                    "type": "text",
                    "label": "City",
                    "placeholder": "New York",
                    "readonly": true,
                    "value": "Riga"
                  },
                  {
                    "name": "editable",
                    "type": "text",
                    "label": "City",
                    "placeholder": "New York",
                    "readonly": false,
                    "value": "Riga"
                  },
                  {
                    "name": "locked_empty",
                    "type": "text",
                    "label": "Postcode",
                    "placeholder": "10001",
                    "readonly": true
                  }
                ]
              }
            ]
          }
        }
        """
    )

    @Test
    fun aLockedFieldIsNotOfferedAsSomethingToTypeInto() {
        renderGroups()

        val locked = compose.onNodeWithTag(field("locked")).assertIsNotEnabled().fetchSemanticsNode()
        val editable = compose.onNodeWithTag(field("editable")).assertIsEnabled().fetchSemanticsNode()

        assertEquals(false, locked.config.getOrNull(SemanticsProperties.IsEditable))
        assertEquals(true, editable.config.getOrNull(SemanticsProperties.IsEditable))

        assertTrue("a locked field offers a way to set its text", SemanticsActions.SetText !in locked.config)
        assertTrue("the editable field lost its SetText action", SemanticsActions.SetText in editable.config)
    }

    @Test
    fun aLockedFieldStillShowsWhatIsInIt() {
        renderGroups()

        compose.onNodeWithTag(field("locked")).assertTextContains("Riga")
    }

    @Test
    fun aLockedFieldWithNothingInItDoesNotHintAtAnInputItWillDiscard() {
        renderGroups()

        compose.onNodeWithText("10001", useUnmergedTree = true).assertDoesNotExist()

        compose.onNodeWithTag(field("locked_empty")).performClick()
        compose.onNodeWithText("10001", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aLockedFieldDoesNotLookLikeAnEditableOne() {
        renderGroups()

        val locked = compose.onNodeWithTag(field("locked")).captureToImage().toPixelMap()
        val editable = compose.onNodeWithTag(field("editable")).captureToImage().toPixelMap()

        assertTrue(
            "the two fields rendered at different sizes, so nothing is being compared",
            locked.width == editable.width && locked.height == editable.height
        )

        var differing = 0
        for (x in 0 until locked.width) {
            for (y in 0 until locked.height) {
                if (locked[x, y] != editable[x, y]) differing++
            }
        }

        val fraction = differing.toDouble() / (locked.width * locked.height)
        assertTrue("a locked field is painted exactly like an editable one", fraction > 0.01)
    }

    private fun field(name: String) = TestTags.field("billing_address", name)

    private fun renderGroups() {
        compose.setContent {
            PayCrossLocalization(sessionLocale = "en", merchantLocale = null) {
                FieldGroupsSection(
                    groups = groups,
                    values = FieldGroupLogic.initialValues(groups),
                    errors = emptyMap(),
                    optedInGroups = emptySet(),
                    modifier = Modifier.width(320.dp),
                    onOptInChange = { _, _ -> },
                    onValueChange = { _, _, _ -> }
                )
            }
        }
    }

    private fun fieldGroups(json: String): List<FieldGroup> =
        Gson().fromJson(json.trimIndent(), SessionResponse::class.java).data!!.fieldGroups!!
}
