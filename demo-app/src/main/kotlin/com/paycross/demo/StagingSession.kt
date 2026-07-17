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
 * Mints a checkout session on staging with the demo's M2M credentials
 * (demo-app/creds.properties). This is the merchant-backend step of the
 * flow — a real integration does this server-side, never in the app.
 */
object StagingSession {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun create(): String {
        check(BuildConfig.CLIENT_ID.isNotEmpty()) {
            "No staging credentials. Copy demo-app/creds.properties.example to creds.properties and fill it in."
        }
        return createSession(fetchAccessToken())
    }

    private fun fetchAccessToken(): String {
        val request = Request.Builder()
            .url(BuildConfig.TOKEN_URL)
            .header("Authorization", Credentials.basic(BuildConfig.CLIENT_ID, BuildConfig.CLIENT_SECRET))
            .post(FormBody.Builder().add("grant_type", "client_credentials").build())
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Token request failed: HTTP ${response.code}" }
            return JSONObject(response.body!!.string()).getString("access_token")
        }
    }

    private fun createSession(accessToken: String): String {
        val now = System.currentTimeMillis()
        val body = JSONObject()
            .put("amount", 1000)
            .put("currency", "EUR")
            .put("transaction_type", "sale")
            .put("merchant_reference", "ANDROID-DEMO-$now")
            .put("return_url", "https://merchant.example.com/payment/return")
            .put("success_url", "https://merchant.example.com/payment/success")
            .put("save_card_config", JSONObject().put("usage", "card_on_file"))
            .put(
                "customer",
                JSONObject()
                    .put("email", "john.doe@example.com")
                    .put("first_name", "John")
                    .put("last_name", "Doe")
                    .put("phone", "+12025551234")
                    .put("merchant_reference", "CUST-$now")
                    .put(
                        "address",
                        JSONObject().put(
                            "billing",
                            JSONObject()
                                .put("line1", "123 Main Street")
                                .put("line2", "Apt 4B")
                                .put("city", "New York")
                                .put("state", "NY")
                                .put("postal_code", "10001")
                                .put("country", "US")
                        )
                    )
            )

        val request = Request.Builder()
            .url(BuildConfig.PAYMENT_API_URL)
            .header("Authorization", "Bearer $accessToken")
            .header("PayCross-Version", BuildConfig.PAYCROSS_VERSION)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body!!.string()
            check(response.isSuccessful) { "Create session failed: HTTP ${response.code} $text" }
            return JSONObject(text).getString("session_token")
        }
    }
}
