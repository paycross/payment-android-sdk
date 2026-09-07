package com.paycross.sdk.internal.ui

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.paycross.sdk.internal.api.models.ThreeDsAction
import java.net.URI
import java.net.URLEncoder

private val RETURN_HOSTS_SUFFIXES = listOf("pay-cross.com", "test-pay-cross.com")
private const val URL_ENCODING = "UTF-8"

// EMV 3DS requires exact form field names; providers emit variants (e.g.
// Nuvei's cReq), so normalize like the checkout page does before posting.
private val FORM_FIELD_NORMALIZATION = mapOf(
    "cReq" to "creq",
    "CReq" to "creq",
    "threeds_method_data" to "threeDSMethodData"
)

/**
 * WebView composable for handling 3DS fingerprint and challenge flows.
 *
 * POSTs the action data to the ACS exactly once per action and notifies the
 * caller when the flow returns to a PayCross domain. The caller controls
 * visibility: fingerprint runs hidden, the challenge is shown fullscreen.
 *
 * @param action 3DS action containing the URL and POST data
 * @param onComplete Called when 3DS flow completes (returns to our domain)
 * @param onError Called when an SSL or loading error occurs
 * @param modifier Modifier for the composable
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ThreeDsWebView(
    action: ThreeDsAction,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current

    key(action) {
        val webView = remember {
            createSecureWebView(context, action.url, onComplete, onError)
        }

        LaunchedEffect(webView) {
            if (action.method.equals("GET", ignoreCase = true)) {
                webView.loadUrl(action.url)
            } else {
                webView.postUrl(action.url, encodeFormData(action.data).toByteArray())
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                webView.stopLoading()
                webView.clearHistory()
                webView.destroy()
            }
        }

        // The caller's modifier goes on a Compose node of its own rather than on
        // the AndroidView — see TestTags.THREE_DS for why that matters. The Box
        // only relays: it propagates its constraints, so the WebView measures to
        // the same size it did when it wore the modifier itself.
        Box(modifier = modifier, propagateMinConstraints = true) {
            AndroidView(factory = { webView })
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createSecureWebView(
    context: android.content.Context,
    actionUrl: String,
    onComplete: () -> Unit,
    onError: (String) -> Unit
): WebView {
    return WebView(context).apply {
        configureSecureSettings()
        webViewClient = createWebViewClient(actionUrl, onComplete, onError)
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureSecureSettings() {
    settings.apply {
        javaScriptEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        domStorageEnabled = true
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        cacheMode = WebSettings.LOAD_NO_CACHE
    }
}

private fun createWebViewClient(
    actionUrl: String,
    onComplete: () -> Unit,
    onError: (String) -> Unit
): WebViewClient {
    return object : WebViewClient() {
        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: SslError
        ) {
            handler.cancel()
            onError("SSL certificate error")
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (isCompletionUrl(url, actionUrl)) {
                onComplete()
            }
        }

        override fun onReceivedError(
            view: WebView,
            errorCode: Int,
            description: String,
            failingUrl: String
        ) {
            onError(description)
        }
    }
}

/**
 * A navigation back to a PayCross host signals 3DS completion — except the
 * action URL itself, which is on our host for the sandbox provider's
 * simulated ACS and must not complete the step on initial load.
 */
internal fun isCompletionUrl(url: String, actionUrl: String): Boolean =
    url.trimEnd('/') != actionUrl.trimEnd('/') && isReturnUrl(url)

internal fun isReturnUrl(url: String): Boolean {
    val host = try {
        URI(url).host
    } catch (e: java.net.URISyntaxException) {
        null
    } ?: return false
    return RETURN_HOSTS_SUFFIXES.any { host == it || host.endsWith(".$it") }
}

internal fun encodeFormData(data: Map<String, String>?): String {
    if (data.isNullOrEmpty()) return ""

    return data.entries
        .filter { it.value.isNotEmpty() }
        .joinToString("&") { (key, value) ->
            val normalizedKey = FORM_FIELD_NORMALIZATION[key] ?: key
            "${URLEncoder.encode(normalizedKey, URL_ENCODING)}=${URLEncoder.encode(value, URL_ENCODING)}"
        }
}
