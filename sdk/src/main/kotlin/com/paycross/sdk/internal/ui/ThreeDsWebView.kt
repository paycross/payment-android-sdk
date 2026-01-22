package com.paycross.sdk.internal.ui

import android.annotation.SuppressLint
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.paycross.sdk.internal.api.models.ThreeDsAction
import java.net.URLEncoder

private const val PAYCROSS_DOMAIN = "paycross"
private const val PAYCROSS_DOMAIN_ALT = "pay-cross"
private const val URL_ENCODING = "UTF-8"

/**
 * WebView composable for handling 3DS fingerprint and challenge flows.
 *
 * Displays the 3DS authentication page in a secure WebView and notifies
 * the caller when the flow completes or fails.
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

    val webView = remember {
        createSecureWebView(context, onComplete, onError)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.clearHistory()
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier,
        update = { view ->
            val postData = encodeFormData(action.data)
            view.postUrl(action.url, postData.toByteArray())
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun createSecureWebView(
    context: android.content.Context,
    onComplete: () -> Unit,
    onError: (String) -> Unit
): WebView {
    return WebView(context).apply {
        configureSecureSettings()
        webViewClient = createWebViewClient(onComplete, onError)
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
            if (isReturnUrl(url)) {
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

private fun isReturnUrl(url: String): Boolean {
    return url.contains(PAYCROSS_DOMAIN) || url.contains(PAYCROSS_DOMAIN_ALT)
}

private fun encodeFormData(data: Map<String, String>?): String {
    if (data.isNullOrEmpty()) return ""

    return data.entries.joinToString("&") { (key, value) ->
        "${URLEncoder.encode(key, URL_ENCODING)}=${URLEncoder.encode(value, URL_ENCODING)}"
    }
}
