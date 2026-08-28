package com.paycross.sdk.internal.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paycross.sdk.internal.ui.components.CardNumberField
import com.paycross.sdk.internal.ui.components.ExpiryField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Types the card fields one key at a time, the way a shopper does. Nothing else
 * in the suite types at all - the demo app prefills and the E2E scripts paste
 * the whole PAN in one burst - so a formatter that moves the caret can corrupt
 * every hand-entered card number without any existing test noticing.
 */
@RunWith(AndroidJUnit4::class)
class CardInputTypingTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun cardNumberKeepsEveryDigitInOrderWhenTypedOneKeyAtATime() {
        val typed = mutableStateOf("")
        compose.setContent {
            CardNumberField(value = typed.value, onValueChange = { typed.value = it })
        }

        typeOneKeyAtATime(CARD_NUMBER, "1234567890123456")

        assertEquals("1234567890123456", typed.value)
        assertEquals("1234 5678 9012 3456", displayedText(CARD_NUMBER))
    }

    @Test
    fun cardNumberKeepsTheSandboxPanIntact() {
        val typed = mutableStateOf("")
        compose.setContent {
            CardNumberField(value = typed.value, onValueChange = { typed.value = it })
        }

        typeOneKeyAtATime(CARD_NUMBER, "4111111111170000")

        assertEquals("4111111111170000", typed.value)
        assertEquals("4111 1111 1117 0000", displayedText(CARD_NUMBER))
    }

    @Test
    fun cardNumberKeepsA19DigitPanIntact() {
        val typed = mutableStateOf("")
        compose.setContent {
            CardNumberField(value = typed.value, onValueChange = { typed.value = it })
        }

        typeOneKeyAtATime(CARD_NUMBER, "6011111111111111117")

        assertEquals("6011111111111111117", typed.value)
        assertEquals("6011 1111 1111 1111 117", displayedText(CARD_NUMBER))
    }

    @Test
    fun expiryKeepsEveryDigitInOrderWhenTypedOneKeyAtATime() {
        val typed = mutableStateOf("")
        compose.setContent {
            ExpiryField(value = typed.value, onValueChange = { typed.value = it })
        }

        typeOneKeyAtATime(EXPIRY, "1228")

        assertEquals("1228", typed.value)
        assertEquals("12/28", displayedText(EXPIRY))
    }

    private fun typeOneKeyAtATime(contentDescription: String, digits: String) {
        digits.forEach { digit ->
            compose.onNodeWithContentDescription(contentDescription)
                .performTextInput(digit.toString())
            compose.waitForIdle()
        }
    }

    // What TalkBack announces and what a uiautomator dump reports as the node's
    // text, so the grouping has to survive here even though the state is digits.
    private fun displayedText(contentDescription: String): String =
        compose.onNodeWithContentDescription(contentDescription)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.EditableText)
            ?.text
            .orEmpty()

    private companion object {
        const val CARD_NUMBER = "Card number input"
        const val EXPIRY = "Expiry date input"
    }
}
