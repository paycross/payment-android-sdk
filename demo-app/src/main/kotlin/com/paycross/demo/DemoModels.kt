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

enum class ScenarioPreset(val label: String) {
    SANDBOX("Sandbox"),
    NUVEI("Nuvei"),
    SHIFT4("Shift4"),
    EMPTY("Empty")
}

object DemoSeeds {

    fun bodyWithAmount(amount: Int) = """
        {
          "amount": $amount,
          "currency": "EUR",
          "transaction_type": "sale",
          "merchant_reference": "ANDROID-{{timestamp}}",
          "return_url": "https://merchant.example.com/payment/return",
          "success_url": "https://merchant.example.com/payment/success",
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

    val DEFAULT_BODY = bodyWithAmount(1000)

    private data class Seed(
        val name: String,
        val pan: String,
        val cardholder: String = "John Doe",
        val expireYear: String = "30",
        val amount: Int = 1000,
        val save: Boolean = false
    )

    // Mirrors payx-tkg android-demo/scripts/lib/stock-sandbox-scenarios.mjs — names, PANs, and
    // order must stay in sync with the adb scenario runner. Rows …3055/…0069/…0127 are known
    // sandbox gaps (unrouted PANs default to approve) and are kept for runner parity.
    private val SANDBOX_SCENARIOS = listOf(
        Seed("Instant approve (no 3DS)", "4111111111170000"),
        Seed("Frictionless 3DS", "4111111111153063"),
        Seed("3DS challenge → approve", "4111111111153220"),
        Seed("3DS challenge → approve + save card", "4111111111153220", save = true),
        Seed("3DS challenge → decline", "4111111111153055"),
        Seed("Decline: do_not_honor", "4111111111150002"),
        Seed("Decline: insufficient_funds", "4111111111159995"),
        Seed("Decline: fraud_suspected", "4111111111150119"),
        Seed("Decline: card_expired", "4111111111150069"),
        Seed("Decline: invalid_cvv", "4111111111150127"),
        Seed("Provider timeout", "4111111111150051")
    )

    // Mirrors payx-tkg android-demo/scripts/lib/demo-data.mjs nuveiScenarios — same cards,
    // labels, and amounts as the E2E threeDsScenarios.
    private val NUVEI_SCENARIOS = listOf(
        Seed("non-3DS approve", "4000027891380961", cardholder = "FL-BRW1"),
        Seed("frictionless 3DS approve", "4761344136141390", cardholder = "FL-BRW2", amount = 15000),
        Seed("hosted 3DS challenge approve", "2221008123677736", cardholder = "CL-BRW2", amount = 15100),
        Seed("decline", "5333463046218753", cardholder = "Jane Smith")
    )

    // Mirrors payx-tkg android-demo/scripts/lib/demo-data.mjs shift4Scenarios — the E2E
    // SHIFT4_FLOW_SCENARIOS labels; challenge passwords are 0101 / 4445 / 9999.
    private val SHIFT4_SCENARIOS = listOf(
        Seed("Flow A frictionless Visa", "4176660000000027", cardholder = "Test Frictionless Visa", expireYear = "26", amount = 15100),
        Seed("Flow A frictionless Mastercard", "5299990270000368", cardholder = "Test Frictionless Mastercard", expireYear = "26", amount = 15100),
        Seed("Flow B DFP frictionless Visa", "4176660000000068", cardholder = "Test DFP Frictionless Visa", expireYear = "26", amount = 15100),
        Seed("Flow C challenge success Visa", "4176660000000092", cardholder = "Test Challenge Success Visa", expireYear = "26", amount = 15100),
        Seed("Flow D DFP challenge success MC", "5204730000001011", cardholder = "Test DFP Challenge Success MC", expireYear = "26", amount = 15100),
        Seed("Flow C challenge failed MC", "5299910010000015", cardholder = "Test Challenge Failed MC", expireYear = "26", amount = 15100)
    )

    fun scenariosFor(merchant: Merchant, preset: ScenarioPreset): List<Scenario> {
        val seeds = when (preset) {
            ScenarioPreset.SANDBOX -> SANDBOX_SCENARIOS
            ScenarioPreset.NUVEI -> NUVEI_SCENARIOS
            ScenarioPreset.SHIFT4 -> SHIFT4_SCENARIOS
            ScenarioPreset.EMPTY -> return emptyList()
        }
        val basic = if (preset == ScenarioPreset.SANDBOX) {
            listOf(
                Scenario(
                    merchantId = merchant.id,
                    name = "Basic sale (no card prefill)",
                    requestBody = DEFAULT_BODY
                )
            )
        } else {
            emptyList()
        }
        return basic + seeds.map { seed ->
            Scenario(
                merchantId = merchant.id,
                name = seed.name,
                card = CardPrefill(seed.cardholder, seed.pan, "12", seed.expireYear, "123", saveCard = seed.save),
                requestBody = bodyWithAmount(seed.amount)
            )
        }
    }
}
