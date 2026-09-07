package com.paycross.sdk.internal.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.paycross.sdk.internal.api.models.ThreeDsAction
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Does the hidden fingerprint WebView actually run?
 *
 * The fingerprint step is rendered at `size(1.dp).alpha(0f)`. The iOS SDK
 * deliberately does the opposite — full size, full opacity, sent to the back —
 * with a source comment stating that WebKit throttles content it believes is
 * invisible and that a throttled fingerprint silently fails to post its device
 * data. Both cannot be right, and if Android's is throttled then every Android
 * 3DS v2 transaction has been silently degraded in production.
 *
 * The question is not whether `postUrl` fires — that is a direct API call. It is
 * whether the JavaScript in the page the ACS returns still executes, because the
 * real 3DS method form auto-submits from script. So the server here returns a
 * page that submits from JS, and the test asserts the submission arrives.
 */
@RunWith(AndroidJUnit4::class)
class ThreeDsFingerprintRenderingTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var server: MockWebServer
    /// Built by hand rather than via `server.url()`: that calls
    /// getCanonicalHostName, which is a reverse DNS lookup, and reading it from
    /// inside composition throws NetworkOnMainThreadException.
    private lateinit var acsUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        // MockWebServer runs inside the test process on the device, so loopback
        // here is the emulator itself and not the host machine.
        acsUrl = "http://127.0.0.1:${server.port}/acs"
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    /// The ACS 3DS-method page: a form the browser is expected to submit itself.
    private fun enqueueSelfSubmittingPage() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/html")
                .setBody(
                    """
                    <html><body>
                      <form id="f" method="POST" action="/collected">
                        <input type="hidden" name="threeDSMethodData" value="probe" />
                      </form>
                      <script>document.getElementById('f').submit();</script>
                    </body></html>
                    """.trimIndent()
                )
        )
        // The submission itself.
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
    }

    private fun action() = ThreeDsAction(
        url = acsUrl,
        method = "POST",
        data = mapOf("threeDSMethodData" to "probe")
    )

    private fun takeRequest(): RecordedRequest? = server.takeRequest(20, TimeUnit.SECONDS)

    private companion object {
        /** Test-only: the fingerprint carries no identifier in production. */
        const val FINGERPRINT = "paycross.test.fingerprint"
    }

    @Test
    fun hiddenFingerprintWebViewStillExecutesScriptAndPosts() {
        enqueueSelfSubmittingPage()

        compose.setContent {
            ThreeDsWebView(
                action = action(),
                onComplete = {},
                onError = {},
                // Exactly what PaymentActivity uses for the fingerprint step.
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
            )
        }

        val initial = takeRequest()
        assertNotNull("the WebView never posted the 3DS method data at all", initial)
        assertEquals("POST", initial!!.method)
        assertEquals("/acs", initial.requestUrl?.encodedPath)

        val submitted = takeRequest()
        assertNotNull(
            "script in the ACS page did not run: a 1dp, alpha-0 WebView is throttled, " +
                "so the fingerprint silently fails to post device data",
            submitted
        )
        assertEquals("/collected", submitted!!.requestUrl?.encodedPath)
        assertTrue(
            "the auto-submitted form body did not survive",
            submitted.body.readUtf8().contains("threeDSMethodData=probe")
        )
    }

    /**
     * The fingerprint's `size(1.dp).alpha(0f)` now lands on the wrapper instead
     * of on the `AndroidView`, so the WebView takes its size by propagation.
     *
     * Asserts the size, which is what keeps the step out of sight; the alpha is
     * a draw-layer property with no semantics to read, and the layer still
     * encloses the interop view's draw either way. A WebView that measured
     * itself here would put the issuer's page over the sheet, and nothing else
     * in the suite would notice.
     */
    @Test
    fun theHiddenFingerprintWebViewIsStillOneDipAcross() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))

        compose.setContent {
            ThreeDsWebView(
                action = action(),
                onComplete = {},
                onError = {},
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .testTag(FINGERPRINT)
            )
        }

        compose.onNodeWithTag(FINGERPRINT, useUnmergedTree = true)
            .onChildren()
            .onFirst()
            .assertWidthIsEqualTo(1.dp)
            .assertHeightIsEqualTo(1.dp)
    }

    /**
     * The same page in a full-size, fully-opaque WebView — the shape iOS uses.
     * If this passes while the hidden one fails, the geometry is the cause and
     * the fix is to render the fingerprint the way iOS does.
     */
    @Test
    fun fullSizeWebViewExecutesScriptAndPosts() {
        enqueueSelfSubmittingPage()

        compose.setContent {
            ThreeDsWebView(
                action = action(),
                onComplete = {},
                onError = {},
                modifier = Modifier.size(320.dp)
            )
        }

        assertNotNull("no initial POST", takeRequest())
        assertNotNull("script did not run in a full-size WebView either", takeRequest())
    }
}
