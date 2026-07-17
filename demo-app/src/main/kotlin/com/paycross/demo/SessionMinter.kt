package com.paycross.demo

import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Mints a checkout session with a merchant's M2M credentials. This is the
 * merchant-backend step of the flow, performed in-app for testing only —
 * a real integration does this server-side, never in the app.
 */
object SessionMinter {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun create(merchant: Merchant, requestBody: String): String {
        check(merchant.clientId.isNotBlank()) { "Merchant has no client ID" }
        val token = fetchAccessToken(merchant)
        val body = substitutePlaceholders(requestBody)

        val request = Request.Builder()
            .url(merchant.paymentApiUrl)
            .header("Authorization", "Bearer $token")
            .header("PayCross-Version", merchant.paycrossVersion)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body!!.string()
            check(response.isSuccessful) { "Create session failed: HTTP ${response.code} $text" }
            return JSONObject(text).getString("session_token")
        }
    }

    private fun fetchAccessToken(merchant: Merchant): String {
        val request = Request.Builder()
            .url(merchant.tokenUrl)
            .header("Authorization", Credentials.basic(merchant.clientId, merchant.clientSecret))
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Token request failed: HTTP ${response.code}" }
            return JSONObject(response.body!!.string()).getString("access_token")
        }
    }

    fun substitutePlaceholders(body: String): String = body
        .replace("{{timestamp}}", System.currentTimeMillis().toString())
        .replace("{{uuid}}", UUID.randomUUID().toString())
}
