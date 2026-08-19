package com.paycross.sdk.internal.api.models

import com.google.gson.annotations.SerializedName

internal data class BrowserInfo(
    @SerializedName("user_agent") val userAgent: String,
    @SerializedName("ip_address") val ipAddress: String,
    @SerializedName("screen_width") val screenWidth: Int,
    @SerializedName("screen_height") val screenHeight: Int,
    @SerializedName("color_depth") val colorDepth: Int,
    @SerializedName("timezone_offset") val timezoneOffset: Int,
    val language: String,
    @SerializedName("accept_header") val acceptHeader: String,
    @SerializedName("java_enabled") val javaEnabled: Boolean,
    @SerializedName("javascript_enabled") val javascriptEnabled: Boolean
)
