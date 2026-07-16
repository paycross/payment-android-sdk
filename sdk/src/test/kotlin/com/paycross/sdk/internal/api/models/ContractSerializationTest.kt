package com.paycross.sdk.internal.api.models

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class ContractSerializationTest {

    private val gson = Gson()

    @Test
    fun `submit request serializes to the submit-card contract`() {
        val request = SubmitCardRequest(
            session = "jwt-token",
            paymentMethod = "card",
            card = CardData(
                cardholderName = "JOHN DOE",
                pan = "4111111111111111",
                expireYear = "2030",
                expireMonth = "12",
                cvv = "123",
                save = true
            ),
            browserInfo = BrowserInfo(
                userAgent = "ua",
                ipAddress = "203.0.113.10",
                screenWidth = 1080,
                screenHeight = 2400,
                colorDepth = 24,
                timezoneOffset = -180,
                language = "en-US",
                acceptHeader = "text/html",
                javaEnabled = false,
                javascriptEnabled = true
            ),
            fieldGroups = mapOf(
                "billing_address" to mapOf("line1" to "123 Main St", "country" to "US")
            )
        )

        val json = JsonParser.parseString(gson.toJson(request)).asJsonObject

        assertEquals("card", json.get("payment_method").asString)
        assertEquals("jwt-token", json.get("session").asString)
        assertFalse(json.has("wallet_token"))

        val card = json.getAsJsonObject("card")
        assertEquals("4111111111111111", card.get("pan").asString)
        assertEquals("2030", card.get("expire_year").asString)
        assertTrue(card.get("save").asBoolean)
        assertFalse(card.has("saved_uuid"))

        val browser = json.getAsJsonObject("browser_info")
        assertEquals("203.0.113.10", browser.get("ip_address").asString)
        assertEquals(-180, browser.get("timezone_offset").asInt)

        val fieldGroups = json.getAsJsonObject("field_groups")
        assertEquals(
            "123 Main St",
            fieldGroups.getAsJsonObject("billing_address").get("line1").asString
        )
    }

    @Test
    fun `saved card request carries saved_uuid and cvv only`() {
        val request = SubmitCardRequest(
            session = "jwt-token",
            paymentMethod = "card",
            card = CardData(savedUuid = "6f9619ff-8b86-d011-b42d-00cf4fc964ff", cvv = "123"),
            browserInfo = minimalBrowserInfo()
        )

        val card = JsonParser.parseString(gson.toJson(request)).asJsonObject.getAsJsonObject("card")
        assertEquals("6f9619ff-8b86-d011-b42d-00cf4fc964ff", card.get("saved_uuid").asString)
        assertFalse(card.has("pan"))
        assertFalse(card.has("save"))
    }

    @Test
    fun `session response parses the deployed VTL shape`() {
        val json = """
            {
              "session_id": "550e8400-e29b-41d4-a716-446655440000",
              "status": "open",
              "latest_transaction_id": "b0f93f3d-6810-4d60-b717-5eadec0b0b9f",
              "data": {
                "locale": "en",
                "return_url": "https://merchant.example.com/cart",
                "success_url": "https://merchant.example.com/thank-you",
                "merchant_country": "GB",
                "field_groups": [
                  {
                    "key": "billing_address",
                    "label": "Billing address",
                    "fields": [
                      {
                        "name": "country",
                        "type": "select",
                        "label": "Country",
                        "placeholder": null,
                        "required": true,
                        "readonly": false,
                        "value": null,
                        "options": [{"value": "US", "label": "United States"}],
                        "validation": {"pattern": "^[A-Z]{2}${'$'}", "max_length": 2, "messages": {"required": "Required"}}
                      },
                      {
                        "name": "state",
                        "type": "text",
                        "label": "State",
                        "required": false,
                        "readonly": false,
                        "value": null,
                        "condition": {"when": "country", "in": ["US"], "display": "required", "default": "hidden"}
                      }
                    ]
                  }
                ],
                "save_card_config": {"usage": "card_on_file"},
                "saved_cards": [
                  {
                    "uuid": "6f9619ff-8b86-d011-b42d-00cf4fc964ff",
                    "masked_pan": "411111******1111",
                    "card_brand": "visa",
                    "expire_month": "12",
                    "expire_year": "2027",
                    "cardholder_name": "John Doe"
                  }
                ]
              }
            }
        """.trimIndent()

        val response = gson.fromJson(json, SessionResponse::class.java)

        assertEquals("open", response.status)
        assertEquals("b0f93f3d-6810-4d60-b717-5eadec0b0b9f", response.latestTransactionId)

        val data = response.data!!
        assertEquals("en", data.locale)
        assertEquals("GB", data.merchantCountry)
        assertEquals("card_on_file", data.saveCardConfig?.usage)
        assertEquals("visa", data.savedCards?.single()?.cardBrand)

        val group = data.fieldGroups!!.single()
        assertEquals("billing_address", group.key)
        val country = group.fields!!.first()
        assertEquals("select", country.type)
        assertEquals("US", country.options?.single()?.value)
        assertEquals("^[A-Z]{2}$", country.validation?.pattern)

        val state = group.fields!![1]
        assertEquals("country", state.condition?.whenField)
        assertEquals(listOf("US"), state.condition?.whenIn)
        assertEquals("hidden", state.condition?.default)
    }

    @Test
    fun `status response parses optional fields`() {
        val json = """
            {
              "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
              "status": "failed",
              "recovery": "do_not_retry"
            }
        """.trimIndent()

        val response = gson.fromJson(json, StatusResponse::class.java)
        assertEquals("failed", response.status)
        assertEquals("do_not_retry", response.recovery)
        assertNull(response.amount)
        assertNull(response.currency)
        assertNull(response.action)
    }

    @Test
    fun `submit response parses retry_after`() {
        val json = """{"error": "Request already processing", "retry_after": 2}"""
        val response = gson.fromJson(json, SubmitCardResponse::class.java)
        assertEquals(2, response.retryAfter)
        assertNull(response.success)
    }

    private fun minimalBrowserInfo() = BrowserInfo(
        userAgent = "ua",
        ipAddress = "127.0.0.1",
        screenWidth = 1,
        screenHeight = 1,
        colorDepth = 24,
        timezoneOffset = 0,
        language = "en",
        acceptHeader = "*/*",
        javaEnabled = false,
        javascriptEnabled = true
    )
}
