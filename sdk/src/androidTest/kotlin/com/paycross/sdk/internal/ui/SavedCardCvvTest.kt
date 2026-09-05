package com.paycross.sdk.internal.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.SavedCard
import com.paycross.sdk.internal.api.models.SavedCardsConfig
import com.paycross.sdk.internal.api.models.SessionData
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What the form does with a typed CVV when a card is removed under it.
 *
 * The CVV belongs to the card it was typed for, and the removal path is the one
 * way a selection can end without the shopper touching the picker. A removal
 * that failed must not cost them what they typed, which is why the clearing
 * hangs off the card leaving the list rather than off the confirm tap.
 */
@RunWith(AndroidJUnit4::class)
class SavedCardCvvTest {

    @get:Rule
    val compose = createComposeRule()

    private val claims = JwtClaims(
        sessionId = "session-123",
        merchantId = "merchant-456",
        customerId = "customer-1",
        brandingId = null,
        amount = 9999,
        currency = "EUR",
        expiresAt = null
    )

    private val visa = SavedCard(
        uuid = "card-1",
        maskedPan = "453201******0366",
        cardBrand = "visa",
        expireMonth = "12",
        expireYear = "2030",
        cardholderName = "JOHN DOE"
    )

    private val amex = SavedCard(
        uuid = "card-2",
        maskedPan = "374245******1007",
        cardBrand = "amex",
        expireMonth = "01",
        expireYear = "2029",
        cardholderName = null
    )

    @Before
    fun setUp() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
    }

    @Test
    fun removingTheSelectedCardClearsTheCvvTypedForIt() {
        val cards = mutableStateOf(listOf(visa, amex))
        val selected = mutableStateOf<String?>("card-1")
        setContent(cards, selected)

        compose.onNodeWithContentDescription(CVV).performTextInput("123")
        compose.waitForIdle()
        assertEquals(3, cvvLength())

        // What a confirmed removal looks like from the form's side: the card
        // leaves the list and the selection goes with it, in one update.
        cards.value = listOf(amex)
        selected.value = null
        compose.waitForIdle()

        assertEquals(0, cvvLength())
    }

    @Test
    fun aRemovalThatFailedLeavesTheCvvAlone() {
        val cards = mutableStateOf(listOf(visa, amex))
        val selected = mutableStateOf<String?>("card-1")
        val error = mutableStateOf<String?>(null)
        setContent(cards, selected, error)

        compose.onNodeWithContentDescription(CVV).performTextInput("123")
        compose.waitForIdle()

        // What a refused removal looks like from the form's side: the banner
        // appears and the form recomposes, but the card is still on the list and
        // still selected. The shopper keeps what they typed.
        error.value = "Could not remove the card. Try again."
        compose.waitForIdle()

        assertEquals(3, cvvLength())
    }

    @Test
    fun removingAnUnselectedCardLeavesTheCvvAlone() {
        val cards = mutableStateOf(listOf(visa, amex))
        val selected = mutableStateOf<String?>("card-1")
        setContent(cards, selected)

        compose.onNodeWithContentDescription(CVV).performTextInput("123")
        compose.waitForIdle()

        cards.value = listOf(visa)
        compose.waitForIdle()

        assertEquals(3, cvvLength())
    }

    private fun setContent(
        cards: MutableState<List<SavedCard>>,
        selected: MutableState<String?>,
        error: MutableState<String?> = mutableStateOf(null)
    ) {
        compose.setContent {
            CardFormScreen(
                claims = claims,
                sessionData = sessionData(cards.value),
                selectedSavedCardUuid = selected.value,
                error = error.value,
                onSavedCardSelected = { selected.value = it },
                onSubmit = { _, _ -> }
            )
        }
    }

    private fun sessionData(cards: List<SavedCard>) = SessionData(
        locale = null,
        returnUrl = null,
        successUrl = null,
        fieldGroups = null,
        merchantCountry = null,
        saveCardConfig = null,
        savedCards = cards,
        savedCardsConfig = SavedCardsConfig(allowRemoval = true, preselect = false),
        wallets = null,
        accountFunding = null,
        googlePay = null
    )

    // Length, not content: the field masks its value, so what semantics reports
    // is bullets. The distinction the tests need is emptied versus untouched.
    private fun cvvLength(): Int =
        compose.onNodeWithContentDescription(CVV)
            .fetchSemanticsNode()
            .config
            .getOrNull(SemanticsProperties.EditableText)
            ?.text
            ?.length
            ?: 0

    private companion object {
        const val CVV = "CVV input"
    }
}
