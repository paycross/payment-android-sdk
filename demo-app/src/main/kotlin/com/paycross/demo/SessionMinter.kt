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

data class MintedSession(
    val sessionToken: String,
    val sessionId: String,
    val checkoutUrl: String,
    val accessToken: String,
    val sessionUrl: String,
    val sentBody: String
)

/**
 * Mints a checkout session with a merchant's M2M credentials. This is the
 * merchant-backend step of the flow, performed in-app for testing only —
 * a real integration does this server-side, never in the app.
 */
object SessionMinter {

    /** Deep link back into the harness after a browser checkout finishes. */
    const val RETURN_DEEP_LINK = "paycross-demo://result"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Thrown when the API refuses the request; only these are safe to retry. */
    class ApiRejection(message: String) : Exception(message)

    fun create(merchant: Merchant, requestBody: String, deepLinkReturn: Boolean = false): MintedSession {
        check(merchant.clientId.isNotBlank()) { "Merchant has no client ID" }
        val token = fetchAccessToken(merchant)
        val body = substitutePlaceholders(requestBody)

        if (deepLinkReturn) {
            // The API may reject a custom-scheme URL; retry with the body as written.
            // Only on rejection — retrying a timeout would mint a second session.
            try {
                return postSession(merchant, token, withDeepLinkReturn(body))
            } catch (rejection: ApiRejection) {
                return postSession(merchant, token, body)
            }
        }
        return postSession(merchant, token, body)
    }

    fun fetchSession(minted: MintedSession, paycrossVersion: String): JSONObject =
        JSONObject(fetchSessionText(minted.sessionUrl, minted.accessToken, paycrossVersion))

    /** Fetches a session with a fresh token — for inspecting past runs. */
    fun fetchSessionAsMerchant(merchant: Merchant, sessionUrl: String): String =
        fetchSessionText(sessionUrl, fetchAccessToken(merchant), merchant.paycrossVersion)

    private fun fetchSessionText(sessionUrl: String, accessToken: String, paycrossVersion: String): String {
        val request = Request.Builder()
            .url(sessionUrl)
            .header("Authorization", "Bearer $accessToken")
            .header("PayCross-Version", paycrossVersion)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body!!.string()
            check(response.isSuccessful) { "Get session failed: HTTP ${response.code} $text" }
            return text
        }
    }

    /** The merchant API routes the hyphenated path only, as payx-tkg also normalizes. */
    private fun sessionsUrl(merchant: Merchant) =
        merchant.paymentApiUrl.replace("payment_sessions", "payment-sessions")

    private fun postSession(merchant: Merchant, token: String, body: String): MintedSession {
        val request = Request.Builder()
            .url(sessionsUrl(merchant))
            .header("Authorization", "Bearer $token")
            .header("PayCross-Version", merchant.paycrossVersion)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body!!.string()
            if (!response.isSuccessful) {
                throw ApiRejection("Create session failed: HTTP ${response.code} $text")
            }
            val json = JSONObject(text)
            val sessionId = json.getString("id")
            return MintedSession(
                sessionToken = json.getString("session_token"),
                sessionId = sessionId,
                checkoutUrl = json.getString("checkout_url"),
                accessToken = token,
                sessionUrl = "${sessionsUrl(merchant).trimEnd('/')}/$sessionId",
                sentBody = body
            )
        }
    }

    private fun withDeepLinkReturn(body: String): String {
        val json = JSONObject(body)
        json.put("return_url", "$RETURN_DEEP_LINK?nav=return")
        json.put("success_url", "$RETURN_DEEP_LINK?nav=success")
        return json.toString()
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
