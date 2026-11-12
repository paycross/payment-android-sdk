package com.paycross.sdk.internal.util

import android.content.Context
import android.webkit.WebSettings
import com.paycross.sdk.internal.api.models.BrowserInfo
import java.util.Locale
import java.util.TimeZone

internal object BrowserInfoProvider {

    private const val COLOR_DEPTH_ANDROID_STANDARD = 24
    private const val MILLIS_PER_MINUTE = 60000
    private const val DEFAULT_ACCEPT_HEADER =
        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"

    fun collect(context: Context): BrowserInfo {
        val displayMetrics = context.resources.displayMetrics

        return BrowserInfo(
            userAgent = WebSettings.getDefaultUserAgent(context),
            screenWidth = displayMetrics.widthPixels,
            screenHeight = displayMetrics.heightPixels,
            colorDepth = COLOR_DEPTH_ANDROID_STANDARD,
            // WHY: 3DS expects minutes west of UTC, as JS Date.getTimezoneOffset() reports, so the sign is flipped.
            timezoneOffset = -TimeZone.getDefault()
                .getOffset(System.currentTimeMillis()) / MILLIS_PER_MINUTE,
            language = BrowserLanguage.clamp(Locale.getDefault().toLanguageTag()),
            acceptHeader = DEFAULT_ACCEPT_HEADER,
            javaEnabled = false,
            javascriptEnabled = true
        )
    }
}
