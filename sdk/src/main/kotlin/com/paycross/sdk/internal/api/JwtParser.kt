package com.paycross.sdk.internal.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal data class JwtClaims(
    val sessionId: String,
    val merchantId: String,
    val customerId: String,
    val brandingId: String?,
    val amount: Long,
    val currency: String,
    val expiresAt: Long?
) {
    fun isExpired(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
        expiresAt != null && nowEpochSeconds >= expiresAt
}

// WHY: the signature is not verified here; the server verifies it.
internal object JwtParser {
    fun parse(token: String): JwtClaims {
        require(token.isNotEmpty()) { "Token cannot be empty" }

        val parts = token.split(".")
        require(parts.size == 3) { "Invalid JWT format: expected 3 parts separated by dots" }

        val payload = decodePayload(parts[1])
        val json = parseJson(payload)

        return JwtClaims(
            sessionId = json.getString("sub"),
            merchantId = json.getStringOrDefault("merchant", ""),
            customerId = json.getStringOrDefault("customer", ""),
            brandingId = json.getStringOrNull("branding"),
            amount = json.get("amount")?.takeIf { !it.isJsonNull }?.asLong
                ?: throw IllegalArgumentException("Missing required field: amount"),
            currency = json.getString("currency"),
            expiresAt = json.get("exp")?.takeIf { !it.isJsonNull }?.asLong
        )
    }

    // WHY: kotlin.io.encoding.Base64 is used, since java.util.Base64 needs API 26 and minSdk is 24.
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodePayload(encodedPayload: String): String {
        return try {
            val decoded = Base64.UrlSafe
                .withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
                .decode(encodedPayload)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JWT payload encoding", e)
        }
    }

    private fun parseJson(payload: String): JsonObject {
        return try {
            JsonParser.parseString(payload).asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JWT payload JSON", e)
        }
    }

    private fun JsonObject.getString(key: String): String {
        return get(key)?.takeIf { !it.isJsonNull }?.asString
            ?: throw IllegalArgumentException("Missing required field: $key")
    }

    private fun JsonObject.getStringOrDefault(key: String, default: String): String {
        return get(key)?.takeIf { !it.isJsonNull }?.asString ?: default
    }

    private fun JsonObject.getStringOrNull(key: String): String? {
        return get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotEmpty() }
    }
}
