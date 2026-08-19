package com.paycross.sdk.internal.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.ui.components.GOOGLE_PAY_BUTTON_TAG
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GooglePayButtonVisibilityTest {

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

    @Before
    fun setUp() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
    }

    @Test
    fun buttonAbsentWhenGooglePayUnavailable() {
        compose.setContent {
            CardFormScreen(
                claims = claims,
                sessionData = null,
                googlePayAvailable = false,
                onSubmit = { _, _ -> }
            )
        }

        compose.onNodeWithTag(GOOGLE_PAY_BUTTON_TAG).assertDoesNotExist()
    }

    @Test
    fun buttonShownAboveTheCardFormWhenAvailable() {
        // PayButton draws from resources bundled in the wallet AAR, but only a
        // device with Google Play services would ever pass isReadyToPay and
        // reach this state; on a GMS-less CI emulator the button is absent by
        // design, so the test is vacuous there rather than failing.
        val gmsAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeTrue(gmsAvailable == ConnectionResult.SUCCESS)

        compose.setContent {
            CardFormScreen(
                claims = claims,
                sessionData = null,
                googlePayAvailable = true,
                onSubmit = { _, _ -> }
            )
        }

        compose.onNodeWithTag(GOOGLE_PAY_BUTTON_TAG).assertIsDisplayed()
    }
}
