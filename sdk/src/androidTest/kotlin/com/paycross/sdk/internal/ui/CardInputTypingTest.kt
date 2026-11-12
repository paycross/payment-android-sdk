package com.paycross.sdk.internal.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paycross.sdk.internal.ui.components.CardNumberField
import com.paycross.sdk.internal.ui.components.ExpiryField
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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

        typeOneKeyAtATime(TestTags.CARD_NUMBER, "1234567890123456")

        assertEquals("1234567890123456", typed.value)
        assertEquals("1234 5678 9012 3456", displayedText(TestTags.CARD_NUMBER))
    }

    @Test
    fun cardNumberKeepsTheSandboxPanIntact() {
        val typed = mutableStateOf("")
        compose.setContent {
            CardNumberField(value = typed.value, onValueChange = { typed.value = it })
        }

        typeOneKeyAtATime(TestTags.CARD_NUMBER, "4111111111170000")

        assertEquals("4111111111170000", typed.value)
        assertEquals("4111 1111 1117 0000", displayedText(TestTags.CARD_NUMBER))
    }

    @Test
    fun cardNumberKeepsA19DigitPanIntact() {
        val typed = mutableStateOf("")
        compose.setContent {
            CardNumberField(value = typed.value, onValueChange = { typed.value = it })
        }

        typeOneKeyAtATime(TestTags.CARD_NUMBER, "6011111111111111117")

        assertEquals("6011111111111111117", typed.value)
        assertEquals("6011 1111 1111 1111 117", displayedText(TestTags.CARD_NUMBER))
    }

    @Test
    fun expiryKeepsEveryDigitInOrderWhenTypedOneKeyAtATime() {
        val typed = mutableStateOf("")
        compose.setContent {
            ExpiryField(value = typed.value, onValueChange = { typed.value = it })
        }

        typeOneKeyAtATime(TestTags.EXPIRY, "1228")

        assertEquals("1228", typed.value)
        assertEquals("12/28", displayedText(TestTags.EXPIRY))
    }

    private fun typeOneKeyAtATime(tag: String, digits: String) {
        digits.forEach { digit ->
            compose.onNodeWithTag(tag).performTextInput(digit.toString())
            compose.waitForIdle()
        }
    }

    private fun displayedText(tag: String): String =
        compose.onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.EditableText)
            ?.text
            .orEmpty()
}
