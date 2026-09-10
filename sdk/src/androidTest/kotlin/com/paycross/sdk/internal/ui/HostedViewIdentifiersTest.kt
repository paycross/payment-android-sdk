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

/**
 * The two identifiers that sit over a hosted Android view, read out of the
 * accessibility tree rather than the Compose semantics tree.
 *
 * The rest of the instrumented suite cannot see this class of bug. A tag on an
 * `AndroidView` is in a Compose test's tree and in no UiAutomator dump, because
 * the hosted view supplies its own accessibility node and `testTagsAsResourceId`
 * writes the resource id onto Compose's nodes only. #51 shipped green on that
 * blind spot and #54 is what it cost.
 *
 * These ask Compose's own accessibility provider for the node info it would hand
 * an accessibility client — which is where a dump's `resource-id` comes from —
 * rather than driving UiAutomator. UiAutomator reads whichever window is in
 * front, and on the CI emulator that is reliably something else.
 *
 * Each test reads a canary first: a plain `Text` under the same flag. If even
 * that has no resource id then the device is not publishing them at all and the
 * failure is about the device, which the message says.
 */
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
                // The modifier PaymentActivity gives the challenge; the flag is
                // set by the window above it there too.
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

        // What propagateMinConstraints buys: the WebView measures to the size the
        // caller asked of the wrapper, rather than to whatever it prefers inside
        // a 0..320dp box.
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

    /**
     * Stands in for the sheet's root: one node, one flag, everything below it
     * published. The flag is set here rather than on each subject because the
     * fix is about which node carries the tag, not about spreading the flag —
     * `TestTags` keeps it to one per window.
     */
    @OptIn(ExperimentalComposeUiApi::class)
    @Composable
    private fun PublishingWindow(content: @Composable () -> Unit) {
        Column(modifier = Modifier.semantics { testTagsAsResourceId = true }) {
            // Text, not an empty box: a node carrying nothing but a tag can be
            // judged unimportant for accessibility and left out of the tree, and
            // then the canary would be reporting on itself.
            Text(text = "canary", modifier = Modifier.testTag(CANARY))
            content()
        }
    }

    /**
     * The assertion that actually fails when the tag slides back onto the
     * `AndroidView`, and the reason this class exists.
     *
     * Asking the accessibility provider for a node *by id* is not that
     * assertion: it returns the resource id whether or not the node hosts a
     * view, because the block that writes it never checks. What the defect
     * breaks is reachability — a hosting node is replaced in its parent's child
     * list by the view itself, so its own node is orphaned from the tree a dump
     * walks. `AndroidViewHolder` carries semantics of its own, so the hosted
     * view is a child of the wrapper once the tag is in the right place, and is
     * the tagged node itself when it is not.
     */
    private fun assertTagIsOnAWrapper(tag: String) {
        compose.onNodeWithTag(tag, useUnmergedTree = true)
            .onChildren()
            .assertCountEquals(1)
    }

    /**
     * The size the hosted Android view settles at, once the view system has laid
     * it out.
     *
     * [ComposeTestRule.waitForIdle] waits on the composition. A hosted view is
     * measured by the view system afterwards, so the wrapper can still be
     * childless, or carry a 0x0 child, at the moment Compose reports itself
     * idle. On an unloaded machine the two land close enough together to hide
     * that; on a busy one they do not, which is the whole of the flake this
     * replaces.
     *
     * Reading the children as a list rather than through [onFirst] is what makes
     * the wait safe to run before the child exists: an empty list is a state to
     * poll again, not an exception.
     */
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

    /**
     * What an accessibility client — a UiAutomator dump among them — would read
     * as this node's `resource-id`.
     */
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

    /**
     * The outermost view that answers accessibility queries for a virtual tree,
     * which under a Compose activity is the view hosting the composition. Found
     * by what the test needs of it rather than by class name, and outermost
     * because the walk is depth-first from the decor view — a hosted `WebView`
     * has a provider too, but it is further down.
     */
    private fun findComposeView(view: View): View? {
        if (view.accessibilityNodeProvider != null) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findComposeView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private companion object {
        /** Loads without a network: the wrapper's identifier is what is under test. */
        const val BLANK_PAGE = "data:text/html,%3Chtml%3E%3C%2Fhtml%3E"

        const val CANARY = "paycross.test.canary"

        /** Named once so the box asked for and the size asserted cannot drift apart. */
        val CHALLENGE_SIZE = 320.dp

        const val LAYOUT_TIMEOUT_MILLIS = 5_000L
    }
}
