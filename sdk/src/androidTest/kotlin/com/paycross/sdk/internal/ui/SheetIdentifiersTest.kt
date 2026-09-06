package com.paycross.sdk.internal.ui

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.R
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.FieldDefinition
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.SaveCardConfig
import com.paycross.sdk.internal.api.models.SavedCard
import com.paycross.sdk.internal.api.models.SavedCardsConfig
import com.paycross.sdk.internal.api.models.SessionData
import com.paycross.sdk.internal.ui.components.SavedCardSelector
import com.paycross.sdk.internal.util.UiText
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The identifier contract, walked element by element.
 *
 * These strings are what a merchant's UI test types, and iOS publishes the same
 * ones, so a rename is a breaking change rather than a refactor. Asserting them
 * by their literal value rather than through [TestTags] is the point: a typo in
 * the constant would otherwise agree with itself.
 */
@RunWith(AndroidJUnit4::class)
class SheetIdentifiersTest {

    @get:Rule
    val compose = createComposeRule()

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

    @Before
    fun setUp() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
    }

    @Test
    fun everyElementOfTheCardFormCarriesItsIdentifier() {
        compose.setContent {
            CardFormScreen(
                claims = claims,
                sessionData = sessionData(),
                selectedSavedCardUuid = null,
                error = UiText.Resource(R.string.paycross_error_payment_failed),
                onSubmit = { _, _ -> }
            )
        }

        listOf(
            "paycross.amount",
            "paycross.savedCards",
            "paycross.savedCard.card-1",
            "paycross.savedCard.card-1.delete",
            "paycross.useNewCard",
            "paycross.cardNumber",
            "paycross.expiry",
            "paycross.cvv",
            "paycross.cardholderName",
            "paycross.saveCard",
            "paycross.field.billing.city",
            "paycross.errorBanner",
            "paycross.payButton"
        ).forEach { tag ->
            // Exists, not displayed: the form scrolls, so on a short screen the
            // lower half of the list is composed and off-screen. What a merchant
            // needs is that the identifier is there to be found and scrolled to.
            compose.onNodeWithTag(tag).assertExists()
        }
    }

    @Test
    fun theWalletIdentifiersAreCarriedWhereGooglePayCanRender() {
        // Guarded the way GooglePayButtonVisibilityTest guards the same path:
        // Google's PayButton draws from the wallet AAR, and only a device with
        // Play services ever reaches this state. Split out rather than folded
        // into the walk above so that a GMS-less device skips these two ids
        // instead of skipping the other thirteen with them.
        val gmsAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeTrue(gmsAvailable == ConnectionResult.SUCCESS)

        compose.setContent {
            CardFormScreen(
                claims = claims,
                sessionData = sessionData(),
                selectedSavedCardUuid = null,
                googlePayAvailable = true,
                onSubmit = { _, _ -> }
            )
        }

        compose.onNodeWithTag("paycross.walletButton").assertExists()
        compose.onNodeWithTag("paycross.walletDivider").assertExists()
    }

    @Test
    fun aFieldGroupErrorCarriesItsOwnIdentifier() {
        compose.setContent {
            CardFormScreen(
                claims = claims,
                sessionData = sessionData(),
                selectedSavedCardUuid = null,
                onSubmit = { _, _ -> }
            )
        }

        // Tapping Pay with an empty required field is what raises it.
        compose.onNodeWithTag("paycross.payButton").performClick()

        // Unmerged: the message is the field's supporting text, and the field
        // merges its descendants, so it has no node of its own once merged. A
        // UiAutomator dump sees the field's error state instead.
        compose.onNodeWithTag("paycross.field.billing.city.error", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun theRemoveDialogAndItsButtonsCarryTheirIdentifiers() {
        compose.setContent {
            SavedCardSelector(
                savedCards = listOf(visa),
                selectedCard = null,
                allowRemoval = true,
                onCardSelected = {}
            )
        }

        compose.onNodeWithTag("paycross.savedCard.card-1.delete").performClick()

        compose.onNodeWithTag("paycross.removeDialog").assertIsDisplayed()
        compose.onNodeWithTag("paycross.removeConfirm").assertIsDisplayed()
        compose.onNodeWithTag("paycross.removeDismiss").assertIsDisplayed()
        assertPublishesItsTags("paycross.removeDialog")
    }

    @Test
    fun theCancelDialogAndItsButtonsCarryTheirIdentifiers() {
        compose.setContent {
            CancelConfirmationDialog(onConfirm = {}, onDismiss = {})
        }

        compose.onNodeWithTag("paycross.cancelDialog").assertIsDisplayed()
        compose.onNodeWithTag("paycross.cancelConfirm").assertIsDisplayed()
        compose.onNodeWithTag("paycross.cancelDismiss").assertIsDisplayed()
        assertPublishesItsTags("paycross.cancelDialog")
    }

    /**
     * A tag becomes a resource id in a UiAutomator dump only when the flag is
     * set in the same window, and Compose stops looking at the semantics root it
     * finds the node under. A dialog is a window of its own, so the sheet's copy
     * of the flag does not reach it — and nothing else in this suite would
     * notice, because the Compose test rule reads every window whether the flag
     * is there or not.
     *
     * This asserts the flag, not the dump. Only the rig can see a dump, and the
     * E2E driver task is where these six ids get exercised for real.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    private fun assertPublishesItsTags(tag: String) {
        assertEquals(
            true,
            compose.onNodeWithTag(tag)
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsPropertiesAndroid.TestTagsAsResourceId)
        )
    }

    @Test
    fun theLoadingOverlayCarriesItsIdentifier() {
        compose.setContent { LoadingOverlay() }

        compose.onNodeWithTag("paycross.loading").assertIsDisplayed()
    }

    private fun sessionData() = SessionData(
        locale = null,
        returnUrl = null,
        successUrl = null,
        fieldGroups = listOf(
            FieldGroup(
                key = "billing",
                label = "Billing",
                fields = listOf(
                    FieldDefinition(
                        name = "city",
                        type = "text",
                        label = "City",
                        placeholder = null,
                        required = true,
                        readonly = null,
                        value = null,
                        condition = null,
                        options = null,
                        validation = null
                    )
                )
            )
        ),
        merchantCountry = null,
        saveCardConfig = SaveCardConfig(usage = "optional"),
        savedCards = listOf(visa),
        savedCardsConfig = SavedCardsConfig(allowRemoval = true, preselect = false),
        wallets = null,
        accountFunding = null,
        googlePay = null
    )
}
