package com.paycross.sdk.internal.ui

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.SessionResponse
import com.paycross.sdk.internal.ui.components.FieldGroupsSection
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A select showing its prompt must not read as a select that has been answered.
 *
 * Moving the prompt into the value slot is what makes this a risk: Material
 * floats the label off an occupied box, so an untouched country select renders
 * in the same shape a completed one does, and only the colour is left to tell
 * the shopper which of the two they are looking at. A form scanned for what is
 * still outstanding gives the wrong answer otherwise.
 *
 * The two selects here are configured identically and are made to draw the same
 * word — the prompt of one is the chosen option's label of the other — so the
 * only thing that can separate them on screen is the colour the prompt is drawn
 * in. The device screenshot is FLAG_SECURE-proof here because the pixels are
 * read back off the composition rather than off the screen.
 */
@RunWith(AndroidJUnit4::class)
class SelectPromptContrastTest {

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
                    "name": "unanswered",
                    "type": "select",
                    "label": "Country",
                    "placeholder": "France",
                    "options": [
                      {"value": "FR", "label": "France"},
                      {"value": "LV", "label": "Latvia"}
                    ]
                  },
                  {
                    "name": "answered",
                    "type": "select",
                    "label": "Country",
                    "placeholder": "France",
                    "options": [
                      {"value": "FR", "label": "France"},
                      {"value": "LV", "label": "Latvia"}
                    ]
                  }
                ]
              }
            ]
          }
        }
        """
    )

    @Test
    fun aPromptIsDrawnMoreFaintlyThanAnAnswer() {
        renderGroups()

        // Both boxes say the same word, so nothing but colour is under test.
        compose.onNodeWithTag(field("unanswered")).assertTextContains("France")
        compose.onNodeWithTag(field("answered")).assertTextContains("France")

        val prompt = compose.onNodeWithTag(field("unanswered")).captureToImage().toPixelMap()
        val answer = compose.onNodeWithTag(field("answered")).captureToImage().toPixelMap()

        assertTrue(
            "the two selects rendered at different sizes, so nothing is being compared",
            prompt.width == answer.width && prompt.height == answer.height
        )

        var differing = 0
        for (x in 0 until prompt.width) {
            for (y in 0 until prompt.height) {
                if (prompt[x, y] != answer[x, y]) differing++
            }
        }
        assertTrue(
            "a select showing its prompt is painted exactly like one holding an answer",
            differing.toDouble() / (prompt.width * prompt.height) > 0.001
        )

        // And faint in the right direction. The label and the border are the same
        // in both, so the darkest pixel of each box is its own value text: the
        // prompt's is the placeholder colour, the answer's is body text.
        assertTrue(
            "the prompt is not lighter than the answer beside it",
            darkest(prompt) > darkest(answer)
        )
    }

    private fun darkest(pixels: androidx.compose.ui.graphics.PixelMap): Float {
        var min = 1f
        for (x in 0 until pixels.width) {
            for (y in 0 until pixels.height) {
                val l = pixels[x, y].luminance()
                if (l < min) min = l
            }
        }
        return min
    }

    private fun field(name: String) = TestTags.field("billing_address", name)

    private fun renderGroups() {
        compose.setContent {
            PayCrossLocalization(sessionLocale = "en", merchantLocale = null) {
                FieldGroupsSection(
                    groups = groups,
                    values = mapOf("billing_address" to mapOf("answered" to "FR")),
                    errors = emptyMap(),
                    optedInGroups = emptySet(),
                    onOptInChange = { _, _ -> },
                    modifier = Modifier.width(320.dp),
                    onValueChange = { _, _, _ -> }
                )
            }
        }
    }

    private fun fieldGroups(json: String): List<FieldGroup> =
        Gson().fromJson(json.trimIndent(), SessionResponse::class.java).data!!.fieldGroups!!
}
