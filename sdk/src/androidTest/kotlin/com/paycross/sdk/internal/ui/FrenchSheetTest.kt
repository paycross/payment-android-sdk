package com.paycross.sdk.internal.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paycross.sdk.internal.api.models.SavedCard
import com.paycross.sdk.internal.ui.components.CardNumberField
import com.paycross.sdk.internal.ui.components.SavedCardSelector
import com.paycross.sdk.internal.ui.components.savedCardDeleteTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Proves values-fr actually reaches the screen, and that it reaches the two
 * dialogs as well.
 *
 * The dialogs are the whole reason the language travels on a composition local
 * of its own: a Compose Dialog runs in a sub-composition against its own window
 * and re-provides LocalContext from it, so a locale carried there would stop at
 * the dialog's edge and leave the shopper reading French under an English
 * "Remove this card?".
 */
@RunWith(AndroidJUnit4::class)
class FrenchSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val french = InstrumentationRegistry.getInstrumentation().targetContext
        .localizedResources(Locale.FRENCH)

    private val visa = SavedCard(
        uuid = "card-1",
        maskedPan = "453201******0366",
        cardBrand = "visa",
        expireMonth = "12",
        expireYear = "2030",
        cardholderName = "JOHN DOE"
    )

    @Test
    fun payButtonIsFrenchUnderAFrenchLocale() {
        compose.setContent {
            CompositionLocalProvider(LocalPayCrossResources provides french) {
                PayButton(amount = "12,34 €", isLoading = false, onClick = {})
            }
        }

        compose.onNodeWithText("Payer 12,34 €").assertIsDisplayed()
    }

    @Test
    fun cardFieldLabelAndSpokenLabelAreBothFrench() {
        compose.setContent {
            CompositionLocalProvider(LocalPayCrossResources provides french) {
                CardNumberField(value = "", onValueChange = {})
            }
        }

        compose.onNodeWithContentDescription("Saisie du numéro de carte").assertIsDisplayed()
        compose.onNodeWithText("Numéro de carte").assertIsDisplayed()
    }

    @Test
    fun theSheetStaysEnglishWithNoResourcesProvided() {
        // The fallback path, which is what a shopper on an English device gets.
        compose.setContent { PayButton(amount = "€12.34", isLoading = false, onClick = {}) }

        compose.onNodeWithText("Pay €12.34").assertIsDisplayed()
    }

    @Test
    fun cancelDialogIsFrenchInsideItsOwnWindow() {
        compose.setContent {
            CompositionLocalProvider(LocalPayCrossResources provides french) {
                CancelConfirmationDialog(onConfirm = {}, onDismiss = {})
            }
        }

        compose.onNodeWithText("Annuler le paiement ?").assertIsDisplayed()
        compose.onNodeWithText("Voulez-vous vraiment annuler ce paiement ?").assertIsDisplayed()
        compose.onNodeWithText("Oui, annuler").assertIsDisplayed()
        compose.onNodeWithText("Continuer le paiement").assertIsDisplayed()
    }

    @Test
    fun removeCardDialogIsFrenchInsideItsOwnWindow() {
        compose.setContent {
            CompositionLocalProvider(LocalPayCrossResources provides french) {
                SavedCardSelector(
                    savedCards = listOf(visa),
                    selectedCard = null,
                    allowRemoval = true,
                    onCardSelected = {}
                )
            }
        }

        compose.onNodeWithText("Utiliser une nouvelle carte").assertIsDisplayed()
        compose.onNodeWithText("Expire 12/30").assertIsDisplayed()
        compose.onNodeWithContentDescription("Supprimer la carte, Visa •••• 0366")
            .assertIsDisplayed()

        compose.onNodeWithTag(savedCardDeleteTag("card-1")).performClick()

        compose.onNodeWithText("Supprimer cette carte ?").assertIsDisplayed()
        compose.onNodeWithText(
            "La carte Visa •••• 0366 ne sera plus proposée pour vos prochains paiements."
        ).assertIsDisplayed()
        compose.onNodeWithText("Supprimer").assertIsDisplayed()
        compose.onNodeWithText("Conserver").assertIsDisplayed()
    }
}
