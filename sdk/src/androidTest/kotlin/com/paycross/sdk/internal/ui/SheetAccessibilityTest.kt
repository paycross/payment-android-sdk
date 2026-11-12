package com.paycross.sdk.internal.ui

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsPropertiesAndroid
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import com.paycross.sdk.internal.ui.components.CardNumberField
import com.paycross.sdk.internal.ui.components.FieldGroupsSection
import com.paycross.sdk.internal.ui.components.SavedCardSelector
import com.paycross.sdk.internal.util.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class SheetAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    private val english = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .localizedResources(Locale.ENGLISH)

    private val claims = JwtClaims(
        sessionId = "session-123",
        merchantId = "merchant-456",
        customerId = "customer-1",
        brandingId = null,
        amount = 1234,
        currency = "EUR",
        expiresAt = null
    )

    private fun setContent(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalPayCrossResources provides english, content = content)
        }
    }

    private fun string(id: Int, vararg args: Any): String = english.getString(id, *args)

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
    fun everyControlIsAtLeastAFingertipTall() {
        setContent {
            CardFormScreen(
                claims = claims,
                sessionData = SessionData(
                    locale = null,
                    returnUrl = null,
                    successUrl = null,
                    fieldGroups = null,
                    merchantCountry = null,
                    saveCardConfig = SaveCardConfig(usage = "optional"),
                    savedCards = null,
                    savedCardsConfig = null,
                    wallets = null,
                    accountFunding = null,
                    googlePay = null
                ),
                onSubmit = { _, _ -> }
            )
        }

        compose.onNodeWithTag(TestTags.PAY_BUTTON).assertHeightIsAtLeast(MIN_TOUCH_TARGET)
        compose.onNodeWithTag(TestTags.SAVE_CARD).assertHeightIsAtLeast(MIN_TOUCH_TARGET)
    }

    @Test
    fun theDeleteButtonIsAtLeastAFingertipTall() {
        setContent {
            SavedCardSelector(
                savedCards = listOf(visa),
                selectedCard = null,
                allowRemoval = true,
                onCardSelected = {}
            )
        }

        compose.onNodeWithTag(TestTags.savedCardDelete("card-1"))
            .assertHeightIsAtLeast(MIN_TOUCH_TARGET)
            .assertWidthIsAtLeast(MIN_TOUCH_TARGET)
    }

    @Test
    fun anInvalidCardFieldSpeaksItsLabelAndItsErrorTogether() {
        setContent {
            CardNumberField(
                value = "4",
                error = string(R.string.paycross_card_number_invalid),
                onValueChange = {}
            )
        }

        val config = compose.onNodeWithTag(TestTags.CARD_NUMBER).fetchSemanticsNode().config

        assertEquals(
            listOf(string(R.string.paycross_card_number_field)),
            config.getOrNull(SemanticsProperties.ContentDescription)
        )
        assertTrue(
            "label missing from the merged node",
            config.getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .any { it.text == string(R.string.paycross_card_number) }
        )
        assertNotNull(
            "error state missing from the merged node",
            config.getOrNull(SemanticsProperties.Error)
        )
    }

    @Test
    fun aValidCardFieldCarriesNoErrorState() {
        setContent {
            CardNumberField(value = "4532015112830366", onValueChange = {})
        }

        val config = compose.onNodeWithTag(TestTags.CARD_NUMBER).fetchSemanticsNode().config
        assertEquals(null, config.getOrNull(SemanticsProperties.Error))
    }

    @Test
    fun theErrorBannerAnnouncesItselfAndDrawsAShapeAsWellAsAColour() {
        setContent {
            CardFormScreen(
                claims = claims,
                sessionData = null,
                error = UiText.Resource(R.string.paycross_error_payment_failed),
                onSubmit = { _, _ -> }
            )
        }

        val config = compose.onNodeWithTag(TestTags.ERROR_BANNER).fetchSemanticsNode().config

        assertEquals(
            LiveRegionMode.Polite,
            config.getOrNull(SemanticsProperties.LiveRegion)
        )
        assertTrue(
            "the message is not on the announced node",
            config.getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .any { it.text == string(R.string.paycross_error_payment_failed) }
        )

        compose.onNodeWithTag(TestTags.ERROR_BANNER_ICON, useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun theSaveCardRowIsOneNamedToggleRatherThanACheckboxBesideASentence() {
        setContent {
            CardFormScreen(
                claims = claims,
                sessionData = SessionData(
                    locale = null,
                    returnUrl = null,
                    successUrl = null,
                    fieldGroups = null,
                    merchantCountry = null,
                    saveCardConfig = SaveCardConfig(usage = "optional"),
                    savedCards = null,
                    savedCardsConfig = null,
                    wallets = null,
                    accountFunding = null,
                    googlePay = null
                ),
                onSubmit = { _, _ -> }
            )
        }

        val toggle = compose.onNodeWithTag(TestTags.SAVE_CARD)
        toggle.assertIsOff()
        assertTrue(
            "the toggle is unnamed",
            toggle.fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .any { it.text == string(R.string.paycross_save_this_card) }
        )

        toggle.performScrollTo().performClick()
        compose.onNodeWithTag(TestTags.SAVE_CARD).assertIsOn()
    }

    @Test
    fun theAmountIsAHeadingAScreenReaderCanJumpTo() {
        setContent {
            CardFormScreen(claims = claims, sessionData = null, onSubmit = { _, _ -> })
        }

        val config = compose.onNodeWithTag(TestTags.AMOUNT).fetchSemanticsNode().config
        assertNotNull(
            "the amount is not marked as a heading",
            config.getOrNull(SemanticsProperties.Heading)
        )
    }

    @Test
    fun thePayButtonKeepsItsNameWhileTheSpinnerIsUp() {
        compose.setContent { PayButton(amount = AMOUNT, isLoading = true, onClick = {}) }

        val config = compose.onNodeWithTag(TestTags.PAY_BUTTON).fetchSemanticsNode().config
        assertEquals(
            listOf(string(R.string.paycross_pay_amount, AMOUNT)),
            config.getOrNull(SemanticsProperties.ContentDescription)
        )
    }

    @Test
    fun theRestingPayButtonIsNamedByItsLabelAlone() {
        compose.setContent { PayButton(amount = AMOUNT, isLoading = false, onClick = {}) }

        val config = compose.onNodeWithTag(TestTags.PAY_BUTTON).fetchSemanticsNode().config
        assertEquals(null, config.getOrNull(SemanticsProperties.ContentDescription))
        assertTrue(
            "the label is not on the button",
            config.getOrNull(SemanticsProperties.Text)
                .orEmpty()
                .any { it.text == string(R.string.paycross_pay_amount, AMOUNT) }
        )
    }

    @Test
    fun theLoadingOverlayIsOneNamedNode() {
        compose.setContent { LoadingOverlay() }

        val config = compose.onNodeWithTag(TestTags.LOADING).fetchSemanticsNode().config
        assertEquals(
            listOf(string(R.string.paycross_processing)),
            config.getOrNull(SemanticsProperties.ContentDescription)
        )
    }

    @Test
    fun theCancelDialogNamesItsPane() {
        compose.setContent { CancelConfirmationDialog(onConfirm = {}, onDismiss = {}) }

        val config = compose.onNodeWithTag(TestTags.CANCEL_DIALOG).fetchSemanticsNode().config
        assertEquals(
            string(R.string.paycross_cancel_payment_title),
            config.getOrNull(SemanticsProperties.PaneTitle)
        )
    }

    @Test
    fun aMerchantFieldIsNamedByItsLabelAndSaysThatItIsRequired() {
        setFieldGroups()

        val config = compose.onNodeWithTag(TestTags.field("customer_info", "email"))
            .fetchSemanticsNode().config

        assertEquals(
            listOf("Email address"),
            config.getOrNull(SemanticsProperties.ContentDescription)
        )
        assertEquals(
            string(R.string.paycross_field_required_state),
            config.getOrNull(SemanticsProperties.StateDescription)
        )
    }

    @Test
    fun anOptionalMerchantFieldIsNamedWithoutBeingCalledRequired() {
        setFieldGroups()

        val config = compose.onNodeWithTag(TestTags.field("customer_info", "phone"))
            .fetchSemanticsNode().config

        assertEquals(
            listOf("Phone number"),
            config.getOrNull(SemanticsProperties.ContentDescription)
        )
        assertEquals(null, config.getOrNull(SemanticsProperties.StateDescription))
    }

    @Test
    fun anInvalidMerchantFieldCarriesTheMessageTheServerSentAsItsError() {
        setFieldGroups(errors = mapOf("customer_info|email" to UiText.Raw("Email address is required")))

        val config = compose.onNodeWithTag(TestTags.field("customer_info", "email"))
            .fetchSemanticsNode().config

        assertEquals("Email address is required", config.getOrNull(SemanticsProperties.Error))
        compose.onNodeWithTag(
            TestTags.fieldError("customer_info", "email"),
            useUnmergedTree = true
        ).assertIsDisplayed()
    }

    private val merchantGroups = listOf(
        FieldGroup(
            key = "customer_info",
            label = "Your details",
            fields = listOf(
                FieldDefinition(
                    name = "email",
                    type = "email",
                    label = "Email address",
                    placeholder = null,
                    required = true,
                    readonly = false,
                    value = null,
                    condition = null,
                    options = null,
                    validation = null
                ),
                FieldDefinition(
                    name = "phone",
                    type = "tel",
                    label = "Phone number",
                    placeholder = null,
                    required = false,
                    readonly = false,
                    value = null,
                    condition = null,
                    options = null,
                    validation = null
                )
            )
        )
    )

    private fun setFieldGroups(errors: Map<String, UiText> = emptyMap()) {
        compose.setContent {
            PayCrossLocalization(sessionLocale = "en", merchantLocale = null) {
                FieldGroupsSection(
                    groups = merchantGroups,
                    values = emptyMap(),
                    errors = errors,
                    optedInGroups = emptySet(),
                    onOptInChange = { _, _ -> },
                    onValueChange = { _, _, _ -> }
                )
            }
        }
    }

    private companion object {
        val MIN_TOUCH_TARGET = 48.dp

        const val AMOUNT = "€12.34"
    }
}
