package com.paycross.sdk.internal.util

import android.content.Context
import android.webkit.WebSettings
import com.paycross.sdk.internal.api.models.BrowserInfo
import java.util.Locale
import java.util.TimeZone

/**
 * Collects browser/device information required for 3DS authentication.
 *
 * This information is sent to the payment provider during 3DS flows to help
 * assess transaction risk and determine authentication requirements.
 */
internal object BrowserInfoProvider {

    private const val COLOR_DEPTH_ANDROID_STANDARD = 24
    private const val MILLIS_PER_MINUTE = 60000
    private const val DEFAULT_ACCEPT_HEADER =
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

    /**
     * Collects browser and device information from the current context.
     *
     * @param context Android context used to access display metrics and WebView settings
     * @return BrowserInfo containing device characteristics for 3DS
     */
    fun collect(context: Context): BrowserInfo {
        val displayMetrics = context.resources.displayMetrics

        return BrowserInfo(
            userAgent = WebSettings.getDefaultUserAgent(context),
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels,
            colorDepth = COLOR_DEPTH_ANDROID_STANDARD,
            // Minutes west of UTC with DST applied, matching the JS
            // Date.getTimezoneOffset() convention 3DS expects.
            timezoneOffset = -TimeZone.getDefault()
                .getOffset(System.currentTimeMillis()) / MILLIS_PER_MINUTE,
            language = BrowserLanguage.clamp(Locale.getDefault().toLanguageTag()),
            acceptHeader = DEFAULT_ACCEPT_HEADER,
            javaEnabled = false,
            javascriptEnabled = true
        )
    }
}
