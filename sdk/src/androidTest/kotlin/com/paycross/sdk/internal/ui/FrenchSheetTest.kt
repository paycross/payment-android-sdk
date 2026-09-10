package com.paycross.sdk.internal.ui

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.FieldDefinition
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.SavedCard
import com.paycross.sdk.internal.ui.components.CardNumberField
import com.paycross.sdk.internal.ui.components.FieldGroupsSection
import com.paycross.sdk.internal.ui.components.SavedCardSelector
import org.junit.Assert.assertEquals
import org.junit.Before
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

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val french = targetContext.localizedResources(Locale.FRENCH)

    // Provided explicitly rather than left to the fallback, so the English cases
    // assert English rather than whatever language the emulator happens to be in.
    private val english = targetContext.localizedResources(Locale.ENGLISH)

    private val claims = JwtClaims(
        sessionId = "session-123",
        merchantId = "merchant-456",
        customerId = "customer-1",
        brandingId = null,
        amount = 1234,
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

    private val requiredEmail = listOf(
        FieldGroup(
            key = "customer_info",
            label = "Your details",
            labels = mapOf("en" to "Your details", "fr" to "Vos coordonnées"),
            fields = listOf(
                FieldDefinition(
                    name = "email",
                    type = "email",
                    label = "Email address",
                    labels = mapOf("en" to "Email address", "fr" to "Adresse e-mail"),
                    placeholder = null,
                    required = true,
                    readonly = false,
                    value = null,
                    condition = null,
                    options = null,
                    validation = null
                )
            )
        )
    )

    @Before
    fun setUp() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
    }

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
        // Unmerged: the field sets a contentDescription of its own and merges its
        // descendants, so the label is only its own node before merging.
        compose.onNodeWithText("Numéro de carte", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun theSheetIsEnglishUnderAnEnglishLocale() {
        compose.setContent {
            CompositionLocalProvider(LocalPayCrossResources provides english) {
                PayButton(amount = "€12.34", isLoading = false, onClick = {})
            }
        }

        compose.onNodeWithText("Pay €12.34").assertIsDisplayed()
    }

    @Test
    fun aLanguageTheSdkCannotSpeakStillFormatsTheAmountItsOwnWay() {
        // The point of keeping the amount's locale separate from the strings':
        // German words do not exist here, German number formatting does.
        compose.setContent {
            CompositionLocalProvider(
                LocalPayCrossResources provides english,
                LocalPayCrossFormattingLocale provides Locale.GERMANY
            ) {
                CardFormScreen(claims = claims, sessionData = null, onSubmit = { _, _ -> })
            }
        }

        // Substring, and stopping before the currency symbol: CLDR separates the
        // number from the euro sign with a no-break space whose width has moved
        // between ICU releases, and which one it is today is not what this test
        // is about. "Pay" is the English word; "12,34" is the German comma.
        compose.onNodeWithText("Pay 12,34", substring = true).assertIsDisplayed()
    }

    // --- Through the real wiring, with nothing provided by hand ---

    @Test
    fun aFrenchSessionDrawsFrenchWithoutAHandProvidedLocal() {
        // Everything above provides the resources itself, which proves the
        // mechanism but skips the ladder. This one hands PayCrossLocalization the
        // session locale exactly as the activity does and reads the words back.
        compose.setContent {
            PayCrossLocalization(sessionLocale = "fr", merchantLocale = null) {
                PayButton(amount = "12,34 €", isLoading = false, onClick = {})
            }
        }

        compose.onNodeWithText("Payer 12,34 €").assertIsDisplayed()
    }

    @Test
    fun aMerchantOverrideBeatsTheSessionThroughTheRealWiring() {
        compose.setContent {
            PayCrossLocalization(sessionLocale = "en", merchantLocale = "fr-CA") {
                PayButton(amount = "12,34 €", isLoading = false, onClick = {})
            }
        }

        compose.onNodeWithText("Payer 12,34 €").assertIsDisplayed()
    }

    @Test
    fun aSessionLanguageTheSdkCannotSpeakDrawsEnglishThroughTheRealWiring() {
        // The device rung is pinned to Japanese rather than inherited from the
        // emulator, so the English here is the ladder's last resort and not the
        // language the machine happened to be in.
        compose.setContent {
            WithDeviceLanguages(Locale.JAPAN) {
                PayCrossLocalization(sessionLocale = "de", merchantLocale = null) {
                    CardFormScreen(claims = claims, sessionData = null, onSubmit = { _, _ -> })
                }
            }
        }

        // English words, and the amount punctuated the German way the session
        // asked for, which is the split the two locals exist to make.
        compose.onNodeWithText("Pay 12,34", substring = true).assertIsDisplayed()
    }

    @Test
    fun aSecondDevicePreferenceIsReachedThroughTheRealWiring() {
        // A handset set to German first and French second has asked for French
        // over English, and reading only the first entry answered it with
        // English. This is that fix, end to end on a device.
        compose.setContent {
            WithDeviceLanguages(Locale.GERMANY, Locale.FRANCE) {
                PayCrossLocalization(sessionLocale = null, merchantLocale = null) {
                    PayButton(amount = "12,34 €", isLoading = false, onClick = {})
                }
            }
        }

        compose.onNodeWithText("Payer 12,34 €").assertIsDisplayed()
    }

    @Test
    fun aRequiredMerchantFieldSaysSoInFrenchThroughTheRealWiring() {
        // Two sources meeting on one node: the name is the merchant's own French
        // label, the state is values-fr. The marker the label is drawn with stays
        // out of the name, which is why the state has to carry the word at all.
        compose.setContent {
            PayCrossLocalization(sessionLocale = "fr", merchantLocale = null) {
                FieldGroupsSection(
                    groups = requiredEmail,
                    values = emptyMap(),
                    errors = emptyMap(),
                    optedInGroups = emptySet(),
                    onOptInChange = { _, _ -> },
                    onValueChange = { _, _, _ -> }
                )
            }
        }

        val config = compose.onNodeWithTag(TestTags.field("customer_info", "email"))
            .fetchSemanticsNode().config

        assertEquals("Obligatoire", config.getOrNull(SemanticsProperties.StateDescription))
        assertEquals(
            listOf("Adresse e-mail"),
            config.getOrNull(SemanticsProperties.ContentDescription)
        )
    }

    /**
     * Runs [content] as though the shopper had listed [languages], in that order,
     * so a test does not inherit whichever language the emulator is in.
     */
    @Composable
    private fun WithDeviceLanguages(vararg languages: Locale, content: @Composable () -> Unit) {
        val configuration = Configuration(LocalConfiguration.current).apply {
            setLocales(LocaleList(*languages))
        }
        CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
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

        compose.onNodeWithTag(TestTags.savedCardDelete("card-1")).performClick()

        compose.onNodeWithText("Supprimer cette carte ?").assertIsDisplayed()
        compose.onNodeWithText(
            "La carte Visa •••• 0366 ne sera plus proposée pour vos prochains paiements."
        ).assertIsDisplayed()
        compose.onNodeWithText("Supprimer").assertIsDisplayed()
        compose.onNodeWithText("Conserver").assertIsDisplayed()
    }
}
