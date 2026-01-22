package com.paycross.sdk.internal.api

import org.json.JSONObject
import java.util.Base64

/**
 * Claims extracted from a PayCross session JWT token.
 *
 * @property sessionId Unique session identifier (from "sub" claim)
 * @property merchantId Merchant identifier
 * @property customerId Customer identifier
 * @property brandingId Optional branding configuration ID
 * @property amount Payment amount as a decimal
 * @property currency ISO 4217 currency code
 */
data class JwtClaims(
    val sessionId: String,
    val merchantId: String,
    val customerId: String,
    val brandingId: String?,
    val amount: Double,
    val currency: String
)

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
            merchantId = json.optString("merchant", ""),
            customerId = json.optString("customer", ""),
            brandingId = json.optString("branding").takeIf { it.isNotEmpty() },
            amount = json.getDouble("amount"),
            currency = json.getString("currency")
        )
    }

    private fun decodePayload(encodedPayload: String): String {
        return try {
            val decoder = Base64.getUrlDecoder()
            val decoded = decoder.decode(encodedPayload)
            String(decoded, Charsets.UTF_8)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JWT payload encoding", e)
        }
    }

    private fun parseJson(payload: String): JSONObject {
        return try {
            JSONObject(payload)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid JWT payload JSON", e)
        }
    }
}
