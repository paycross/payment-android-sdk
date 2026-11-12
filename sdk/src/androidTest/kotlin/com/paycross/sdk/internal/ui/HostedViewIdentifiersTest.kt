package com.paycross.sdk.internal.ui

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.ThreeDsAction
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HostedViewIdentifiersTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

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
    }

    @Test
    fun theThreeDsChallengeCarriesItsIdentifierWhereADumpReadsIt() {
        compose.setContent {
            PublishingWindow {
                ThreeDsWebView(
                    action = ThreeDsAction(url = BLANK_PAGE, method = "GET", data = null),
                    onComplete = {},
                    onError = {},
                    modifier = Modifier
                        .size(CHALLENGE_SIZE)
                        .testTag(TestTags.THREE_DS)
                )
            }
        }
        compose.waitForIdle()
        assertCanaryIsPublished()

        val measured = awaitHostedViewLayout(TestTags.THREE_DS)
        assertTagIsOnAWrapper(TestTags.THREE_DS)

        val expected = with(compose.density) { CHALLENGE_SIZE.roundToPx() }
        assertEquals(
            "the WebView hosted under ${TestTags.THREE_DS} measured " +
                "${measured.width}x${measured.height}px inside a ${expected}px box",
            IntSize(expected, expected),
            measured
        )

        assertEquals(
            "paycross.threeDS is in the semantics tree and not in the accessibility " +
                "tree a dump reads: the tag is on the AndroidView, whose own node " +
                "replaces the Compose one",
            TestTags.THREE_DS,
            resourceIdFor(TestTags.THREE_DS)
        )
    }

    @Test
    fun theWalletButtonCarriesItsIdentifierWhereADumpReadsIt() {
        val gmsAvailable = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeTrue(gmsAvailable == ConnectionResult.SUCCESS)

        compose.setContent {
            PublishingWindow {
                CardFormScreen(
                    claims = claims,
                    sessionData = null,
                    googlePayAvailable = true,
                    onSubmit = { _, _ -> }
                )
            }
        }
        compose.waitForIdle()
        assertCanaryIsPublished()

        assertTagIsOnAWrapper(TestTags.WALLET_BUTTON)

        assertEquals(
            "paycross.walletButton is in the semantics tree and not in the " +
                "accessibility tree a dump reads: the tag is on the AndroidView " +
                "holding Google's button, whose own node replaces the Compose one",
            TestTags.WALLET_BUTTON,
            resourceIdFor(TestTags.WALLET_BUTTON)
        )
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun PublishingWindow(content: @Composable () -> Unit) {
        Column(modifier = Modifier.semantics { testTagsAsResourceId = true }) {
            Text(text = "canary", modifier = Modifier.testTag(CANARY))
            content()
        }
    }

    private fun assertTagIsOnAWrapper(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .onChildren()
            .assertCountEquals(1)
    }

    private fun awaitHostedViewLayout(tag: String): IntSize {
        var size = IntSize.Zero
        try {
            compose.waitUntil(LAYOUT_TIMEOUT_MILLIS) {
                size = hostedViewSize(tag)
                size != IntSize.Zero
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError(
                "the WebView hosted under $tag was still ${size.width}x${size.height}px " +
                    "after $LAYOUT_TIMEOUT_MILLIS ms, so the view system had not laid it out",
                timeout
            )
        }
        return size
    }

    private fun hostedViewSize(tag: String): IntSize =
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .onChildren()
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.size
            ?: IntSize.Zero

    private fun assertCanaryIsPublished() {
        assertEquals(
            "a plain Compose node under the flag has no resource id either, so this " +
                "device is not publishing them at all and cannot tell the fix from " +
                "the bug",
            CANARY,
            resourceIdFor(CANARY)
        )
    }

    private fun resourceIdFor(tag: String): String? {
        val nodeId = compose.onNodeWithTag(tag).fetchSemanticsNode().id
        return compose.runOnUiThread {
            val provider = requireNotNull(composeView().accessibilityNodeProvider) {
                "the Compose view exposes no accessibility node provider"
            }
            provider.createAccessibilityNodeInfo(nodeId)?.viewIdResourceName
        }
    }

    private fun composeView(): View =
        requireNotNull(findComposeView(compose.activity.window.decorView)) {
            "no AndroidComposeView under the activity's decor view"
        }

    private fun findComposeView(view: View): View? {
        if (view.accessibilityNodeProvider != null) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findComposeView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private companion object {
        const val BLANK_PAGE = "data:text/html,%3Chtml%3E%3C%2Fhtml%3E"

        const val CANARY = "paycross.test.canary"

        val CHALLENGE_SIZE = 320.dp

        const val LAYOUT_TIMEOUT_MILLIS = 5_000L
    }
}
