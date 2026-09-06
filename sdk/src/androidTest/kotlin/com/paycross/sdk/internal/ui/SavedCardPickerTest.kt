package com.paycross.sdk.internal.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paycross.sdk.internal.api.models.SavedCard
import com.paycross.sdk.internal.ui.components.SavedCardSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedCardPickerTest {

    @get:Rule
    val compose = createComposeRule()

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
        cardBrand = "AMERICAN EXPRESS",
        expireMonth = "01",
        expireYear = "2029",
        cardholderName = null
    )

    @Test
    fun rowsRenderForEverySavedCardPlusUseANewCard() {
        compose.setContent {
            SavedCardSelector(
                savedCards = listOf(visa, amex),
                selectedCard = visa,
                onCardSelected = {}
            )
        }

        compose.onNodeWithTag(TestTags.savedCard("card-1")).assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag(TestTags.savedCard("card-2")).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.USE_NEW_CARD).assertIsDisplayed()
        compose.onNodeWithText("Visa •••• 0366").assertIsDisplayed()
        compose.onNodeWithText("American Express •••• 1007").assertIsDisplayed()
    }

    @Test
    fun deleteIconIsAbsentWhenTheSessionDoesNotAllowRemoval() {
        compose.setContent {
            SavedCardSelector(
                savedCards = listOf(visa, amex),
                selectedCard = null,
                allowRemoval = false,
                onCardSelected = {}
            )
        }

        compose.onNodeWithTag(TestTags.savedCardDelete("card-1")).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.savedCardDelete("card-2")).assertDoesNotExist()
    }

    @Test
    fun deleteIsDisabledButStillShownWhileAPaymentIsInFlight() {
        compose.setContent {
            SavedCardSelector(
                savedCards = listOf(visa, amex),
                selectedCard = visa,
                allowRemoval = true,
                removalEnabled = false,
                onCardSelected = {}
            )
        }

        // Shown, so the rows do not reflow under the shopper mid-authorization,
        // but dead to a tap.
        compose.onNodeWithTag(TestTags.savedCardDelete("card-1")).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithTag(TestTags.savedCardDelete("card-2")).assertIsNotEnabled()
    }

    @Test
    fun confirmingTheDialogRemovesThatCard() {
        var removed: String? = null

        compose.setContent {
            SavedCardSelector(
                savedCards = listOf(visa, amex),
                selectedCard = null,
                allowRemoval = true,
                onCardSelected = {},
                onCardRemoved = { removed = it }
            )
        }

        compose.onNodeWithTag(TestTags.savedCardDelete("card-2")).performClick()
        // The removal is destructive and irreversible, so the tap alone must not
        // fire it: the dialog stands between the icon and the callback.
        assertNull(removed)

        compose.onNodeWithTag(TestTags.REMOVE_CONFIRM).performClick()
        assertEquals("card-2", removed)
    }

    @Test
    fun keepingTheDialogLeavesTheCardAlone() {
        var removed: String? = null

        compose.setContent {
            SavedCardSelector(
                savedCards = listOf(visa),
                selectedCard = null,
                allowRemoval = true,
                onCardSelected = {},
                onCardRemoved = { removed = it }
            )
        }

        compose.onNodeWithTag(TestTags.savedCardDelete("card-1")).performClick()
        compose.onNodeWithTag(TestTags.REMOVE_DISMISS).performClick()

        assertNull(removed)
        compose.onNodeWithTag(TestTags.savedCard("card-1")).assertIsDisplayed()
    }

    @Test
    fun tappingARowSelectsThatCardAndUseANewCardClearsIt() {
        val picked = mutableListOf<String?>()

        compose.setContent {
            SavedCardSelector(
                savedCards = listOf(visa, amex),
                selectedCard = null,
                onCardSelected = { picked.add(it?.uuid) }
            )
        }

        compose.onNodeWithTag(TestTags.savedCard("card-2")).performClick()
        compose.onNodeWithTag(TestTags.USE_NEW_CARD).performClick()

        assertEquals(listOf("card-2", null), picked)
    }
}
