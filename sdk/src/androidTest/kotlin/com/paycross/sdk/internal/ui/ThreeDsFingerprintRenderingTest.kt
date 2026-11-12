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

@RunWith(AndroidJUnit4::class)
class ThreeDsFingerprintRenderingTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var acsUrl: String

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        acsUrl = "http://127.0.0.1:${server.port}/acs"
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

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
        server.enqueue(MockResponse().setResponseCode(200).setBody("ok"))
    }

    private fun action() = ThreeDsAction(
        url = acsUrl,
        method = "POST",
        data = mapOf("threeDSMethodData" to "probe")
    )

    private fun takeRequest(): RecordedRequest? = server.takeRequest(20, TimeUnit.SECONDS)

    private companion object {
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
