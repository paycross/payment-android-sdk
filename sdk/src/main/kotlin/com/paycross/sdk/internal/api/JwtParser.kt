package com.paycross.sdk.internal.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Claims extracted from a PayCross session JWT token.
 *
 * @property sessionId Unique session identifier (from "sub" claim)
 * @property merchantId Merchant identifier
 * @property customerId Customer identifier
 * @property brandingId Optional branding configuration ID
 * @property amount Payment amount in minor units (e.g. cents)
 * @property currency ISO 4217 currency code
 * @property expiresAt Token expiry as epoch seconds (from "exp" claim)
 */
data class JwtClaims(
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

/**
 * Parses PayCross session JWT tokens to extract payment claims.
 *
 * This parser extracts the payload from a JWT without verifying the signature,
 * as signature verification is handled server-side. The parser is used to
 * extract session metadata for display and API calls.
 */
internal object JwtParser {
    /**
     * Parses a JWT token and extracts the payment claims.
     *
     * @param token The JWT token string in format "header.payload.signature"
     * @return [JwtClaims] containing the extracted payment information
     * @throws IllegalArgumentException if the token format is invalid or payload cannot be decoded
     */
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

    // kotlin.io.encoding.Base64, not java.util.Base64: the latter is API 26 while
    // minSdk is 24, so on Android 7.x it threw NoClassDefFoundError - an Error,
    // which the catch below does not catch - and every payment hard-crashed on the
    // first token parse. The Kotlin one is pure stdlib with no API level floor and
    // still decodes on the JVM, so this stays unit-testable.
    @OptIn(ExperimentalEncodingApi::class)
    private fun decodePayload(encodedPayload: String): String {
        return try {
            // JWT payloads are base64url and unpadded.
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
