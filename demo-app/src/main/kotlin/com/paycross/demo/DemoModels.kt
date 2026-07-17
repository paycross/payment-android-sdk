package com.paycross.demo

import com.paycross.sdk.PayCrossEnvironment
import com.paycross.sdk.TestCardPrefill
import java.util.UUID

data class Merchant(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val environment: String = ENV_STAGING,
    val tokenUrl: String = "",
    val clientId: String = "",
    val clientSecret: String = "",
    val paymentApiUrl: String = "",
    val paycrossVersion: String = ""
) {
    val sdkEnvironment: PayCrossEnvironment
        get() = if (environment == ENV_PRODUCTION) {
            PayCrossEnvironment.PRODUCTION
        } else {
            PayCrossEnvironment.STAGING
        }

    companion object {
        const val ENV_STAGING = "STAGING"
        const val ENV_PRODUCTION = "PRODUCTION"
    }
}

data class CardPrefill(
    val cardholderName: String = "",
    val pan: String = "",
    val expireMonth: String = "",
    val expireYear: String = "",
    val cvv: String = ""
) {
    fun toTestCardPrefillOrNull(): TestCardPrefill? =
        if (pan.isBlank()) {
            null
        } else {
            TestCardPrefill(cardholderName, pan, expireMonth, expireYear, cvv)
        }
}

data class Scenario(
    val id: String = UUID.randomUUID().toString(),
    val merchantId: String,
    val name: String,
    val card: CardPrefill = CardPrefill(),
    val requestBody: String
)

data class DemoData(
    val merchants: List<Merchant> = emptyList(),
    val scenarios: List<Scenario> = emptyList(),
    val selectedMerchantId: String? = null
)

object DemoSeeds {

    val DEFAULT_BODY = """
        {
          "amount": 1000,
          "currency": "EUR",
          "transaction_type": "sale",
          "merchant_reference": "ANDROID-{{timestamp}}",
          "return_url": "https://merchant.example.com/payment/return",
          "success_url": "https://merchant.example.com/payment/success",
          "save_card_config": { "usage": "card_on_file" },
          "customer": {
            "email": "john.doe@example.com",
            "first_name": "John",
            "last_name": "Doe",
            "phone": "+12025551234",
            "merchant_reference": "CUST-{{timestamp}}",
            "address": {
              "billing": {
                "line1": "123 Main Street",
                "line2": "Apt 4B",
                "city": "New York",
                "state": "NY",
                "postal_code": "10001",
                "country": "US"
              }
            }
          }
        }
    """.trimIndent()

    // Sandbox provider test PANs — see payment-sandbox/docs/README.md.
    private val SANDBOX_SCENARIOS = listOf(
        "3DS challenge → approve" to "4111111111153220",
        "Frictionless 3DS" to "4111111111153063",
        "Instant approve (no 3DS)" to "4111111111170000",
        "Decline: do_not_honor" to "4111111111150002",
        "Decline: insufficient_funds" to "4111111111159995",
        "Provider timeout" to "4111111111150051"
    )

    fun scenariosFor(merchant: Merchant, sandbox: Boolean): List<Scenario> {
        if (!sandbox) {
            return listOf(
                Scenario(
                    merchantId = merchant.id,
                    name = "Basic sale €10.00",
                    requestBody = DEFAULT_BODY
                )
            )
        }
        return SANDBOX_SCENARIOS.map { (name, pan) ->
            Scenario(
                merchantId = merchant.id,
                name = name,
                card = CardPrefill("John Doe", pan, "12", "2028", "123"),
                requestBody = DEFAULT_BODY
            )
        }
    }
}
