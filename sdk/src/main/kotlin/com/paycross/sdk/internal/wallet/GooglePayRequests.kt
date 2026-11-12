package com.paycross.sdk.internal.wallet

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.SessionData
import com.paycross.sdk.internal.util.Amounts

internal object GooglePayRequests {
    private const val API_VERSION = 2
    private const val API_VERSION_MINOR = 0

    // WHY: PAN_ONLY is an FPAN with no liability shift, yet its tokens can still trigger a 3DS challenge.
    private val AUTH_METHODS = listOf("PAN_ONLY", "CRYPTOGRAM_3DS")
    private val CARD_NETWORKS = listOf("VISA", "MASTERCARD", "AMEX", "DISCOVER", "JCB")

    // WHY: The account_funding block is not a gate: wallet payments are accepted on account-funding transfers.
    fun isSessionEligible(sessionData: SessionData?): Boolean =
        sessionData?.wallets?.googlePay != false

    // WHY: IsReadyToPay carries no tokenization or billing parameters: it asks about the device, not the gateway.
    fun buildIsReadyToPayRequest(): JsonObject = JsonObject().apply {
        addProperty("apiVersion", API_VERSION)
        addProperty("apiVersionMinor", API_VERSION_MINOR)
        add("allowedPaymentMethods", JsonArray().apply { add(baseCardMethod()) })
    }

    fun buildAllowedPaymentMethods(claims: JwtClaims, sessionData: SessionData?): JsonArray =
        JsonArray().apply { add(cardMethodWithTokenization(claims, sessionData)) }

    // WHY: Google translates its own chrome but not a merchant's label, so totalPriceLabel arrives translated.
    fun buildPaymentDataRequest(
        claims: JwtClaims,
        sessionData: SessionData?,
        googlePayMerchantId: String?,
        totalPriceLabel: String
    ): JsonObject = JsonObject().apply {
        addProperty("apiVersion", API_VERSION)
        addProperty("apiVersionMinor", API_VERSION_MINOR)
        add("allowedPaymentMethods", buildAllowedPaymentMethods(claims, sessionData))
        add("transactionInfo", transactionInfo(claims, sessionData, totalPriceLabel))
        merchantInfo(sessionData, googlePayMerchantId)?.let { add("merchantInfo", it) }
    }

    private fun baseCardMethod(): JsonObject = JsonObject().apply {
        addProperty("type", "CARD")
        add("parameters", JsonObject().apply {
            add("allowedAuthMethods", toJsonArray(AUTH_METHODS))
            add("allowedCardNetworks", toJsonArray(CARD_NETWORKS))
        })
    }

    private fun cardMethodWithTokenization(claims: JwtClaims, sessionData: SessionData?): JsonObject {
        val method = baseCardMethod()

        // WHY: Requesting the billing address adds a consent step for the shopper, so it is opt-in per merchant.
        if (sessionData?.googlePay?.billingAddressRequired == true) {
            method.getAsJsonObject("parameters").apply {
                addProperty("billingAddressRequired", true)
                add("billingAddressParameters", JsonObject().apply { addProperty("format", "FULL") })
            }
        }

        // WHY: PAYMENT_GATEWAY, not DIRECT: the ECv2 envelope is decrypted server-side, never on the device.
        method.add("tokenizationSpecification", JsonObject().apply {
            addProperty("type", "PAYMENT_GATEWAY")
            add("parameters", JsonObject().apply {
                addProperty("gateway", "paycross")
                addProperty("gatewayMerchantId", claims.merchantId)
            })
        })

        return method
    }

    private fun transactionInfo(
        claims: JwtClaims,
        sessionData: SessionData?,
        totalPriceLabel: String
    ): JsonObject =
        JsonObject().apply {
            addProperty("totalPriceStatus", "FINAL")
            addProperty("totalPrice", Amounts.toMajorString(claims.amount, claims.currency))
            addProperty("totalPriceLabel", totalPriceLabel)
            addProperty("currencyCode", claims.currency)
            addProperty("countryCode", sessionData?.merchantCountry ?: "US")
        }

    private fun merchantInfo(sessionData: SessionData?, googlePayMerchantId: String?): JsonObject? {
        val merchantName = sessionData?.googlePay?.merchantName?.takeIf { it.isNotBlank() }
        // WHY: Google requires the Business Console id only in production; TEST works without one.
        val merchantId = googlePayMerchantId?.takeIf { it.isNotBlank() }
        if (merchantName == null && merchantId == null) return null

        return JsonObject().apply {
            merchantName?.let { addProperty("merchantName", it) }
            merchantId?.let { addProperty("merchantId", it) }
        }
    }

    private fun toJsonArray(values: List<String>): JsonArray =
        JsonArray().apply { values.forEach(::add) }
}
