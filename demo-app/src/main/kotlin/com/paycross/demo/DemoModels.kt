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
    val cvv: String = "",
    val saveCard: Boolean = false
) {
    fun toTestCardPrefillOrNull(): TestCardPrefill? =
        if (pan.isBlank()) {
            null
        } else {
            TestCardPrefill(cardholderName, pan, expireMonth, expireYear, cvv, saveCard)
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

    private data class SandboxSeed(val name: String, val pan: String, val save: Boolean = false)

    // Sandbox provider test PANs — see payment-sandbox/docs/README.md and
    // payment_testing_go/templates/paycross/.
    private val SANDBOX_SCENARIOS = listOf(
        SandboxSeed("Instant approve (no 3DS)", "4111111111170000"),
        SandboxSeed("Frictionless 3DS", "4111111111153063"),
        SandboxSeed("3DS challenge → approve", "4111111111153220"),
        SandboxSeed("3DS challenge → approve + save card", "4111111111153220", save = true),
        SandboxSeed("3DS challenge → decline", "4111111111153055"),
        SandboxSeed("Decline: do_not_honor", "4111111111150002"),
        SandboxSeed("Decline: insufficient_funds", "4111111111159995"),
        SandboxSeed("Decline: fraud_suspected", "4111111111150119"),
        SandboxSeed("Decline: card_expired", "4111111111150069"),
        SandboxSeed("Decline: invalid_cvv", "4111111111150127"),
        SandboxSeed("Provider timeout", "4111111111150051")
    )

    fun scenariosFor(merchant: Merchant): List<Scenario> {
        val basic = Scenario(
            merchantId = merchant.id,
            name = "Basic sale (no card prefill)",
            requestBody = DEFAULT_BODY
        )
        return listOf(basic) + SANDBOX_SCENARIOS.map { seed ->
            Scenario(
                merchantId = merchant.id,
                name = seed.name,
                card = CardPrefill("John Doe", seed.pan, "12", "2028", "123", saveCard = seed.save),
                requestBody = DEFAULT_BODY
            )
        }
    }
}
