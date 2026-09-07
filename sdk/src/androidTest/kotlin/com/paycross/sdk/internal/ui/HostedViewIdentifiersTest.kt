package com.paycross.sdk.internal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.ThreeDsAction
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * The two identifiers that sit over a hosted Android view, read where the E2E
 * rig reads them: the accessibility tree UiAutomator dumps, not the Compose
 * semantics tree.
 *
 * The rest of the instrumented suite cannot see this class of bug. A tag on an
 * `AndroidView` is in a Compose test's tree and in no dump, because the hosted
 * view supplies its own accessibility node and `testTagsAsResourceId` writes the
 * resource id onto Compose's nodes only. #51 shipped green on that blind spot
 * and #54 is what it cost, so these two read the dump instead.
 *
 * Each test composes a canary beside its subject: a plain Compose node under the
 * same flag. If UiAutomator cannot see even that, the device is not publishing
 * resource ids and the test skips rather than blaming the code.
 */
@RunWith(AndroidJUnit4::class)
class HostedViewIdentifiersTest {

    @get:Rule
    val compose = createComposeRule()

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())


    private val claims = JwtClaims(
        sessionId = "session-123",
        merchantId = "merchant-456",
        customerId = "customer-1",
        brandingId = null,
        amount = 1234,
        currency = "EUR",
        expiresAt = null
    )

    @Before
    fun setUp() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
        // The compressed hierarchy drops nodes it judges uninteresting, and a
        // wrapper whose whole job is to carry an id is exactly that. The rig
        // reads the uncompressed tree; so does this.
        device.setCompressedLayoutHierarchy(false)
    }

    @Test
    fun theThreeDsChallengeReachesTheDumpAsAResourceId() {
        compose.setContent {
            PublishingWindow {
                // Exactly the modifier PaymentActivity gives the challenge, minus
                // the fill: the flag is set by the window above it there too.
                ThreeDsWebView(
                    action = ThreeDsAction(url = BLANK_PAGE, method = "GET", data = null),
                    onComplete = {},
                    onError = {},
                    modifier = Modifier
                        .size(320.dp)
                        .testTag(TestTags.THREE_DS)
                )
            }
        }
        compose.waitForIdle()
        requireResourceIdsArePublished()

        assertNotNull(
            "paycross.threeDS is in the semantics tree and not in the dump: the tag " +
                "is on the AndroidView, whose own node replaces the Compose one",
            device.findObject(By.res(TestTags.THREE_DS))
        )
    }

    @Test
    fun theWalletButtonReachesTheDumpAsAResourceId() {
        // Guarded as GooglePayButtonVisibilityTest guards the same path: Google's
        // PayButton draws from the wallet AAR and only a device with Play
        // services ever reaches this state.
        val gmsAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeTrue(gmsAvailable == ConnectionResult.SUCCESS)

        compose.setContent {
            PublishingWindow {
                // The whole screen rather than the section alone: Google's button
                // is initialized from the payment methods the session allows, and
                // a hand-written list is not what it is given in production.
                CardFormScreen(
                    claims = claims,
                    sessionData = null,
                    googlePayAvailable = true,
                    onSubmit = { _, _ -> }
                )
            }
        }
        compose.waitForIdle()
        requireResourceIdsArePublished()

        assertNotNull(
            "paycross.walletButton is in the semantics tree and not in the dump: the " +
                "tag is on the AndroidView holding Google's button, whose own node " +
                "replaces the Compose one",
            device.findObject(By.res(TestTags.WALLET_BUTTON))
        )
    }

    /**
     * Stands in for the sheet's root: one node, one flag, everything below it
     * published. Setting it here rather than on each subject is the point — the
     * fix is about which node carries the tag, not about spreading the flag.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun PublishingWindow(content: @Composable () -> Unit) {
        Column(modifier = Modifier.semantics { testTagsAsResourceId = true }) {
            Column(modifier = Modifier.size(1.dp).testTag(CANARY)) {}
            content()
        }
    }

    /**
     * The canary has to be there before the subject means anything. It fails
     * rather than skipping, and it prints the resource ids the dump does hold:
     * a suite that quietly skips is the blind spot this class was written to
     * close, not something to reproduce inside it.
     */
    private fun requireResourceIdsArePublished() {
        if (device.wait(Until.hasObject(By.res(CANARY)), TIMEOUT_MS) == true) return
        fail(
            "no resource id for a plain Compose node under testTagsAsResourceId. " +
                "The dump holds these ids: ${resourceIdsInTheDump()}"
        )
    }

    private fun resourceIdsInTheDump(): List<String> {
        val xml = ByteArrayOutputStream().use { out ->
            device.dumpWindowHierarchy(out)
            out.toString(Charsets.UTF_8.name())
        }
        return Regex("resource-id=\"([^\"]*)\"")
            .findAll(xml)
            .map { it.groupValues[1] }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    private companion object {
        /** Loads without a network: the wrapper's identifier is what is under test. */
        const val BLANK_PAGE = "data:text/html,%3Chtml%3E%3C%2Fhtml%3E"

        const val CANARY = "paycross.test.canary"
        const val TIMEOUT_MS = 10_000L
    }
}
