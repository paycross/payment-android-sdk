package com.paycross.sdk.internal.util

import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Resolves the device's public IP address for `browser_info.ip_address`,
 * which the submit-card API requires. Mirrors the checkout page: a lookup
 * via ipify with a loopback fallback so submission never blocks on it.
 */
internal object IpAddressProvider {

    private const val LOOKUP_URL = "https://api.ipify.org?format=json"
    private const val FALLBACK_IP = "127.0.0.1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: String? = null

    fun get(): String {
        cached?.let { return it }

        val resolved = lookup() ?: FALLBACK_IP
        cached = resolved
        return resolved
    }

    private fun lookup(): String? {
        return try {
            client.newCall(Request.Builder().url(LOOKUP_URL).build()).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                JsonParser.parseString(body).asJsonObject
                    .get("ip")?.takeIf { !it.isJsonNull }?.asString
                    ?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }
}
