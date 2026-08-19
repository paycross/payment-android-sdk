package com.paycross.sdk.internal.wallet

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.SessionData
import com.paycross.sdk.internal.util.Amounts

/**
 * Builds the Google Pay API request payloads and decides session-level
 * eligibility. Mirrors the checkout page's googlePaySupport.js /
 * walletVisibility.js exactly — the two clients must show the same wallet
 * for the same session snapshot.
 *
 * Requests are built as Gson JSON rather than through the Play Services
 * builders so this stays unit-testable on the JVM; the thin GMS layer
 * ([GooglePayClient]) converts them with `fromJson` at the boundary.
 */
internal object GooglePayRequests {

    private const val API_VERSION = 2
    private const val API_VERSION_MINOR = 0

    // PAN_ONLY is an FPAN with no liability shift; PAN_ONLY tokens can still
    // trigger a real 3DS challenge downstream, which the existing polling
    // machinery already handles.
    private val AUTH_METHODS = listOf("PAN_ONLY", "CRYPTOGRAM_3DS")
    private val CARD_NETWORKS = listOf("VISA", "MASTERCARD", "AMEX", "DISCOVER", "JCB")

    /**
     * Session-level gate for showing Google Pay. Strict-false semantics on the
     * wallets block: sessions snapshotted before the backend shipped `wallets`
     * have no block at all and must keep showing the wallet, so only an
     * explicit `google_pay: false` hides it. AFT sessions never show wallets —
     * core rejects wallet payments on account-funding sessions server-side.
     */
    fun isSessionEligible(sessionData: SessionData?): Boolean =
        sessionData?.wallets?.googlePay != false && sessionData?.accountFunding != true

    /**
     * The IsReadyToPay probe carries only the base CARD method — no
     * tokenizationSpecification, no billing-address parameters. It asks "can
     * this device pay with these networks at all", not "with our gateway".
     */
    fun buildIsReadyToPayRequest(): JsonObject = JsonObject().apply {
        addProperty("apiVersion", API_VERSION)
        addProperty("apiVersionMinor", API_VERSION_MINOR)
        add("allowedPaymentMethods", JsonArray().apply { add(baseCardMethod()) })
    }

    fun buildPaymentDataRequest(
        claims: JwtClaims,
        sessionData: SessionData?,
        googlePayMerchantId: String?
    ): JsonObject = JsonObject().apply {
        addProperty("apiVersion", API_VERSION)
        addProperty("apiVersionMinor", API_VERSION_MINOR)
        add("allowedPaymentMethods", JsonArray().apply { add(cardMethodWithTokenization(claims, sessionData)) })
        add("transactionInfo", transactionInfo(claims, sessionData))
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

        // Asking for the address costs the shopper an extra consent, so it is
        // opt-in per merchant; Google returns it at
        // paymentMethodData.info.billingAddress inside the payload we already
        // forward untouched — only the request has to ask.
        if (sessionData?.googlePay?.billingAddressRequired == true) {
            method.getAsJsonObject("parameters").apply {
                addProperty("billingAddressRequired", true)
                add("billingAddressParameters", JsonObject().apply { addProperty("format", "FULL") })
            }
        }

        // PAYMENT_GATEWAY, not DIRECT: the ECv2 envelope is decrypted
        // server-side by the vault, never on the device. We are the gateway,
        // so gatewayMerchantId is our own merchant UUID from the session JWT;
        // core verifies the decrypted echo of it against the transaction.
        method.add("tokenizationSpecification", JsonObject().apply {
            addProperty("type", "PAYMENT_GATEWAY")
            add("parameters", JsonObject().apply {
                addProperty("gateway", "paycross")
                addProperty("gatewayMerchantId", claims.merchantId)
            })
        })

        return method
    }

    private fun transactionInfo(claims: JwtClaims, sessionData: SessionData?): JsonObject =
        JsonObject().apply {
            addProperty("totalPriceStatus", "FINAL")
            addProperty("totalPrice", Amounts.toMajorString(claims.amount, claims.currency))
            addProperty("totalPriceLabel", "Payment")
            addProperty("currencyCode", claims.currency)
            addProperty("countryCode", sessionData?.merchantCountry ?: "US")
        }

    private fun merchantInfo(sessionData: SessionData?, googlePayMerchantId: String?): JsonObject? {
        val merchantName = sessionData?.googlePay?.merchantName?.takeIf { it.isNotBlank() }
        // The Business Console id is required by Google only in PRODUCTION;
        // without one the TEST environment still works, so it stays optional
        // and the field is simply omitted.
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
