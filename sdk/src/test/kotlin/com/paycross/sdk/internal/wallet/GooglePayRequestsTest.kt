package com.paycross.sdk.internal.wallet

import com.google.gson.JsonObject
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.GooglePayConfig
import com.paycross.sdk.internal.api.models.SessionData
import com.paycross.sdk.internal.api.models.WalletsAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GooglePayRequestsTest {

    private fun claims(amount: Long = 12345, currency: String = "EUR") = JwtClaims(
        sessionId = "session-123",
        merchantId = "6f9619ff-8b86-d011-b42d-00cf4fc964ff",
        customerId = "customer-1",
        brandingId = null,
        amount = amount,
        currency = currency,
        expiresAt = null
    )

    private fun sessionData(
        wallets: WalletsAvailability? = null,
        accountFunding: Boolean? = null,
        googlePay: GooglePayConfig? = null,
        merchantCountry: String? = null
    ) = SessionData(
        locale = null,
        returnUrl = null,
        successUrl = null,
        fieldGroups = null,
        merchantCountry = merchantCountry,
        saveCardConfig = null,
        savedCards = null,
        savedCardsConfig = null,
        wallets = wallets,
        accountFunding = accountFunding,
        googlePay = googlePay
    )

    // --- Gating: strict-false semantics, mirroring the web's walletVisibility ---

    @Test
    fun `session without wallets block is eligible for back-compat`() {
        assertTrue(GooglePayRequests.isSessionEligible(sessionData()))
    }

    @Test
    fun `missing session data is eligible`() {
        assertTrue(GooglePayRequests.isSessionEligible(null))
    }

    @Test
    fun `explicit google_pay false is not eligible`() {
        val data = sessionData(wallets = WalletsAvailability(applePay = null, googlePay = false))
        assertFalse(GooglePayRequests.isSessionEligible(data))
    }

    @Test
    fun `explicit google_pay true is eligible`() {
        val data = sessionData(wallets = WalletsAvailability(applePay = false, googlePay = true))
        assertTrue(GooglePayRequests.isSessionEligible(data))
    }

    @Test
    fun `wallets block with null google_pay is eligible`() {
        val data = sessionData(wallets = WalletsAvailability(applePay = true, googlePay = null))
        assertTrue(GooglePayRequests.isSessionEligible(data))
    }

    @Test
    fun `account funding session with google_pay enabled is eligible`() {
        val data = sessionData(
            wallets = WalletsAvailability(applePay = null, googlePay = true),
            accountFunding = true
        )
        assertTrue(GooglePayRequests.isSessionEligible(data))
    }

    @Test
    fun `account funding session without a wallets block is eligible`() {
        assertTrue(GooglePayRequests.isSessionEligible(sessionData(accountFunding = true)))
    }

    @Test
    fun `account funding session with explicit google_pay false is not eligible`() {
        val data = sessionData(
            wallets = WalletsAvailability(applePay = null, googlePay = false),
            accountFunding = true
        )
        assertFalse(GooglePayRequests.isSessionEligible(data))
    }

    @Test
    fun `account funding false stays eligible`() {
        assertTrue(GooglePayRequests.isSessionEligible(sessionData(accountFunding = false)))
    }

    // --- IsReadyToPay request ---

    @Test
    fun `isReadyToPay request carries only the base card method`() {
        val request = GooglePayRequests.buildIsReadyToPayRequest()

        assertEquals(2, request.get("apiVersion").asInt)
        assertEquals(0, request.get("apiVersionMinor").asInt)

        val methods = request.getAsJsonArray("allowedPaymentMethods")
        assertEquals(1, methods.size())

        val card = methods[0].asJsonObject
        assertEquals("CARD", card.get("type").asString)
        assertFalse(card.has("tokenizationSpecification"))

        val parameters = card.getAsJsonObject("parameters")
        assertEquals(
            listOf("PAN_ONLY", "CRYPTOGRAM_3DS"),
            parameters.getAsJsonArray("allowedAuthMethods").map { it.asString }
        )
        assertEquals(
            listOf("VISA", "MASTERCARD", "AMEX", "DISCOVER", "JCB"),
            parameters.getAsJsonArray("allowedCardNetworks").map { it.asString }
        )
        assertFalse(parameters.has("billingAddressRequired"))
        assertFalse(parameters.has("billingAddressParameters"))
    }

    @Test
    fun `isReadyToPay request never asks for billing address regardless of session config`() {
        // The probe is built without session input at all; this pins the shape.
        val request = GooglePayRequests.buildIsReadyToPayRequest()
        val parameters = request.getAsJsonArray("allowedPaymentMethods")[0]
            .asJsonObject.getAsJsonObject("parameters")
        assertFalse(parameters.has("billingAddressRequired"))
    }

    // --- PaymentData request ---

    private fun cardMethod(request: JsonObject): JsonObject =
        request.getAsJsonArray("allowedPaymentMethods")[0].asJsonObject

    @Test
    fun `payment data request uses PAYMENT_GATEWAY tokenization with the JWT merchant`() {
        val request = GooglePayRequests.buildPaymentDataRequest(claims(), sessionData(), null, TOTAL)

        val spec = cardMethod(request).getAsJsonObject("tokenizationSpecification")
        assertEquals("PAYMENT_GATEWAY", spec.get("type").asString)
        assertEquals("paycross", spec.getAsJsonObject("parameters").get("gateway").asString)
        assertEquals(
            "6f9619ff-8b86-d011-b42d-00cf4fc964ff",
            spec.getAsJsonObject("parameters").get("gatewayMerchantId").asString
        )
    }

    @Test
    fun `transaction info formats a two-decimal currency in major units`() {
        val request = GooglePayRequests.buildPaymentDataRequest(
            claims(amount = 12345, currency = "EUR"),
            sessionData(merchantCountry = "GB"),
            null,
            TOTAL
        )

        val info = request.getAsJsonObject("transactionInfo")
        assertEquals("FINAL", info.get("totalPriceStatus").asString)
        assertEquals("123.45", info.get("totalPrice").asString)
        assertEquals(TOTAL, info.get("totalPriceLabel").asString)
        assertEquals("EUR", info.get("currencyCode").asString)
        assertEquals("GB", info.get("countryCode").asString)
    }

    @Test
    fun `transaction info keeps zero-decimal currencies undivided`() {
        val request = GooglePayRequests.buildPaymentDataRequest(
            claims(amount = 5000, currency = "JPY"),
            sessionData(),
            null,
            TOTAL
        )

        val info = request.getAsJsonObject("transactionInfo")
        assertEquals("5000", info.get("totalPrice").asString)
        assertEquals("JPY", info.get("currencyCode").asString)
    }

    @Test
    fun `country code defaults to US when the session has no merchant country`() {
        val request = GooglePayRequests.buildPaymentDataRequest(claims(), sessionData(), null, TOTAL)
        assertEquals("US", request.getAsJsonObject("transactionInfo").get("countryCode").asString)
    }

    @Test
    fun `country code defaults to US when session data is missing entirely`() {
        val request = GooglePayRequests.buildPaymentDataRequest(claims(), null, null, TOTAL)
        assertEquals("US", request.getAsJsonObject("transactionInfo").get("countryCode").asString)
    }

    @Test
    fun `billing address is requested only when the session requires it`() {
        val requiring = sessionData(
            googlePay = GooglePayConfig(
                merchantOrigin = null,
                merchantName = null,
                billingAddressRequired = true
            )
        )

        val parameters = cardMethod(
            GooglePayRequests.buildPaymentDataRequest(claims(), requiring, null, TOTAL)
        ).getAsJsonObject("parameters")
        assertTrue(parameters.get("billingAddressRequired").asBoolean)
        assertEquals(
            "FULL",
            parameters.getAsJsonObject("billingAddressParameters").get("format").asString
        )
    }

    @Test
    fun `billing address is absent when not required`() {
        val notRequiring = sessionData(
            googlePay = GooglePayConfig(
                merchantOrigin = null,
                merchantName = null,
                billingAddressRequired = false
            )
        )

        val parameters = cardMethod(
            GooglePayRequests.buildPaymentDataRequest(claims(), notRequiring, null, TOTAL)
        ).getAsJsonObject("parameters")
        assertFalse(parameters.has("billingAddressRequired"))
        assertFalse(parameters.has("billingAddressParameters"))
    }

    @Test
    fun `merchant info carries the session merchant name`() {
        val data = sessionData(
            googlePay = GooglePayConfig(
                merchantOrigin = "shop.example.com",
                merchantName = "Example Shop",
                billingAddressRequired = null
            )
        )

        val request = GooglePayRequests.buildPaymentDataRequest(claims(), data, null, TOTAL)
        val merchantInfo = request.getAsJsonObject("merchantInfo")
        assertEquals("Example Shop", merchantInfo.get("merchantName").asString)
        assertFalse(merchantInfo.has("merchantId"))
    }

    @Test
    fun `merchant info carries the Business Console id when configured`() {
        val request = GooglePayRequests.buildPaymentDataRequest(
            claims(),
            sessionData(),
            "BCR2DN4TXXXXXXXX",
            TOTAL
        )

        val merchantInfo = request.getAsJsonObject("merchantInfo")
        assertEquals("BCR2DN4TXXXXXXXX", merchantInfo.get("merchantId").asString)
        assertFalse(merchantInfo.has("merchantName"))
    }

    @Test
    fun `merchant info is omitted when neither name nor id is present`() {
        val request = GooglePayRequests.buildPaymentDataRequest(claims(), sessionData(), null, TOTAL)
        assertNull(request.get("merchantInfo"))
    }

    @Test
    fun `the total line carries whatever label it was handed`() {
        // Google draws this string to the shopper and does not translate it, so
        // it arrives already resolved and this object must not second-guess it.
        val request = GooglePayRequests.buildPaymentDataRequest(
            claims(),
            sessionData(),
            null,
            "Total"
        )

        assertEquals(
            "Total",
            request.getAsJsonObject("transactionInfo").get("totalPriceLabel").asString
        )
    }

    // The English value of paycross_total. The resource itself is resolved by the
    // sheet, which has a Context; this object is handed the result.
    private companion object {
        const val TOTAL = "Total"
    }
}
