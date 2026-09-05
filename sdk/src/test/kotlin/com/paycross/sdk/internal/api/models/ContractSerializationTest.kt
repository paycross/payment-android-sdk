package com.paycross.sdk.internal.api.models

import com.google.gson.Gson
import com.google.gson.JsonObject
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
        assertEquals("ua", browser.get("user_agent").asString)
        assertEquals(-180, browser.get("timezone_offset").asInt)

        val fieldGroups = json.getAsJsonObject("field_groups")
        assertEquals(
            "123 Main St",
            fieldGroups.getAsJsonObject("billing_address").get("line1").asString
        )
    }

    @Test
    fun `field_groups carries non-string values with their JSON types intact`() {
        // The Go side of the contract is map[string]map[string]interface{}, so
        // booleans and numbers must arrive as JSON booleans and numbers, not as
        // their string renderings.
        val request = SubmitCardRequest(
            session = "jwt-token",
            paymentMethod = "card",
            card = CardData(cvv = "123"),
            browserInfo = minimalBrowserInfo(),
            fieldGroups = mapOf(
                "consents" to mapOf("marketing_opt_in" to false, "installments" to 3)
            )
        )

        val fieldGroups = JsonParser.parseString(gson.toJson(request))
            .asJsonObject.getAsJsonObject("field_groups")
        val consents = fieldGroups.getAsJsonObject("consents")
        assertTrue(consents.get("marketing_opt_in").isJsonPrimitive)
        assertFalse(consents.get("marketing_opt_in").asBoolean)
        assertEquals(3, consents.get("installments").asInt)
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
    fun `wallet submit request serializes to the submit-card contract`() {
        val paymentMethodData = JsonParser.parseString(
            """
            {
              "type": "CARD",
              "description": "Visa \u2022\u2022\u2022\u2022 1234",
              "info": {"cardNetwork": "VISA", "cardDetails": "1234"},
              "tokenizationData": {"type": "PAYMENT_GATEWAY", "token": "{\"signature\":\"MEQCIA==\"}"}
            }
            """.trimIndent()
        ).asJsonObject

        val request = SubmitCardRequest(
            session = "jwt-token",
            paymentMethod = "google_pay",
            walletToken = WalletToken(type = "google_pay", data = paymentMethodData),
            browserInfo = minimalBrowserInfo(),
            fieldGroups = mapOf("billing_address" to mapOf("country" to "US"))
        )

        val json = JsonParser.parseString(gson.toJson(request)).asJsonObject

        assertEquals("google_pay", json.get("payment_method").asString)
        // No card block for wallet payments: the edge routes on wallet_token.
        assertFalse(json.has("card"))

        val walletToken = json.getAsJsonObject("wallet_token")
        assertEquals("google_pay", walletToken.get("type").asString)
        assertEquals("VISA", walletToken.getAsJsonObject("data")
            .getAsJsonObject("info").get("cardNetwork").asString)

        assertTrue(json.has("browser_info"))
        assertEquals("US", json.getAsJsonObject("field_groups")
            .getAsJsonObject("billing_address").get("country").asString)
    }

    @Test
    fun `google pay token string survives serialization byte-identical`() {
        // Google's ECv2 signature covers tokenizationData.token byte for byte
        // and the edge forwards it to the vault untouched, so the decoded token
        // string must survive the SDK's serialization round trip exactly:
        // no key reordering of the envelope JSON inside it, no empty-field
        // stripping, no re-encoding. The token here exercises the hazards —
        // base64 '=' padding (Gson escapes it as \u003d on the wire, which
        // decodes back to '='), escaped quotes, an empty string value, and
        // deliberate non-alphabetical key order.
        val token = """{"signature":"MEQCIF4Sd+2u0G0DM4dtd8SyBnMOm0m4RfDNQTgc9SpXo0GhAiB8qk1sQ==",""" +
            """"intermediateSigningKey":{"signedKey":"{\"keyValue\":\"MFkwEwYHKoZI==\",\"keyExpiration\":\"1879788278688\"}",""" +
            """"signatures":["MEYCIQCO=="]},"protocolVersion":"ECv2","signedMessage":"{\"encryptedMessage\":\"ZW5j\",\"ephemeralPublicKey\":\"BPh=\",\"tag\":\"\"}"}"""

        val paymentDataJson = JsonObject().apply {
            add("paymentMethodData", JsonObject().apply {
                addProperty("type", "CARD")
                add("info", JsonObject().apply {
                    addProperty("cardNetwork", "MASTERCARD")
                    addProperty("cardDetails", "4111")
                    addProperty("assuranceDetails", "")
                })
                add("tokenizationData", JsonObject().apply {
                    addProperty("type", "PAYMENT_GATEWAY")
                    addProperty("token", token)
                })
            })
        }.toString()

        // The exact path the ViewModel takes: parse the sheet's JSON, embed
        // paymentMethodData as a Gson tree, serialize the submit request with
        // the same default Gson configuration Retrofit's converter uses.
        val paymentMethodData = JsonParser.parseString(paymentDataJson)
            .asJsonObject.getAsJsonObject("paymentMethodData")
        val request = SubmitCardRequest(
            session = "jwt-token",
            paymentMethod = "google_pay",
            walletToken = WalletToken(type = "google_pay", data = paymentMethodData),
            browserInfo = minimalBrowserInfo()
        )

        val wire = gson.toJson(request)
        val decoded = JsonParser.parseString(wire).asJsonObject
            .getAsJsonObject("wallet_token").getAsJsonObject("data")

        assertEquals(token, decoded.getAsJsonObject("tokenizationData").get("token").asString)
        // Empty fields must not be stripped either — the whole object is opaque.
        assertEquals("", decoded.getAsJsonObject("info").get("assuranceDetails").asString)
        assertEquals(
            JsonParser.parseString(paymentDataJson).asJsonObject.get("paymentMethodData"),
            decoded
        )
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
                "wallets": {"apple_pay": false, "google_pay": true},
                "account_funding": false,
                "google_pay": {
                  "merchant_origin": "shop.example.com",
                  "merchant_name": "Example Shop",
                  "billing_address_required": true
                },
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

        assertEquals(false, data.wallets?.applePay)
        assertEquals(true, data.wallets?.googlePay)
        assertEquals(false, data.accountFunding)
        assertEquals("shop.example.com", data.googlePay?.merchantOrigin)
        assertEquals("Example Shop", data.googlePay?.merchantName)
        assertEquals(true, data.googlePay?.billingAddressRequired)
    }

    @Test
    fun `session response tolerates payloads without wallet fields`() {
        // wallets, account_funding and google_pay are additive: sessions minted
        // before the backend shipped them must still parse.
        val json = """
            {"session_id": "550e8400-e29b-41d4-a716-446655440000", "data": {"locale": "en"}}
        """.trimIndent()

        val response = gson.fromJson(json, SessionResponse::class.java)
        val data = response.data!!
        assertNull(data.wallets)
        assertNull(data.accountFunding)
        assertNull(data.googlePay)
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

    @Test
    fun `browser_info omits ip_address on every submit shape`() {
        // The key has to be absent rather than "" or null. The handler fills
        // browser_info.ip_address from CF-Connecting-IP, falling back to the
        // API Gateway source IP, whenever the client leaves it blank; a value
        // the client does send wins over both, so sending one would override
        // the address the connection actually came from.
        val requests = listOf(
            SubmitCardRequest(
                session = "jwt-token",
                paymentMethod = "card",
                card = CardData(savedUuid = "6f9619ff-8b86-d011-b42d-00cf4fc964ff", cvv = "123"),
                browserInfo = minimalBrowserInfo()
            ),
            SubmitCardRequest(
                session = "jwt-token",
                paymentMethod = "google_pay",
                walletToken = WalletToken(type = "google_pay", data = JsonObject()),
                browserInfo = minimalBrowserInfo()
            )
        )

        for (request in requests) {
            val browser = JsonParser.parseString(gson.toJson(request))
                .asJsonObject.getAsJsonObject("browser_info")
            assertFalse(browser.has("ip_address"))
            // Guards against the whole object silently going missing instead.
            assertEquals("ua", browser.get("user_agent").asString)
        }
    }

    @Test
    fun `session response parses saved_cards_config`() {
        val json = """
            {
              "session_id": "550e8400-e29b-41d4-a716-446655440000",
              "status": "open",
              "data": {
                "saved_cards": [
                  {
                    "uuid": "6f9619ff-8b86-d011-b42d-00cf4fc964ff",
                    "masked_pan": "411111******1111",
                    "card_brand": "visa",
                    "expire_month": "12",
                    "expire_year": "2027",
                    "cardholder_name": "John Doe"
                  }
                ],
                "saved_cards_config": {"allow_removal": true, "preselect": true}
              }
            }
        """.trimIndent()

        val data = gson.fromJson(json, SessionResponse::class.java).data!!

        assertEquals(true, data.savedCardsConfig?.allowRemoval)
        assertEquals(true, data.savedCardsConfig?.preselect)
        assertTrue(data.allowsSavedCardRemoval)
        assertTrue(data.preselectsSavedCard)
    }

    @Test
    fun `session response without saved_cards_config leaves both flags off`() {
        // Sessions minted before the backend shipped the config key carry saved
        // cards and no sibling object. Removal and preselection are opt-in, so
        // the absent config must read as both-false rather than crash a `!!`.
        val json = """
            {
              "session_id": "550e8400-e29b-41d4-a716-446655440000",
              "status": "open",
              "data": {
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

        val data = gson.fromJson(json, SessionResponse::class.java).data!!

        assertNull(data.savedCardsConfig)
        assertFalse(data.allowsSavedCardRemoval)
        assertFalse(data.preselectsSavedCard)
    }

    @Test
    fun `saved card parses a null cardholder_name`() {
        // customer_saved_cards.cardholder_name is nullable in core and the
        // projection passes it through, so the field has to be nullable here or
        // Gson writes null into a non-null Kotlin property and every read of it
        // throws somewhere far from the decode.
        val json = """
            {
              "session_id": "550e8400-e29b-41d4-a716-446655440000",
              "data": {
                "saved_cards": [
                  {
                    "uuid": "6f9619ff-8b86-d011-b42d-00cf4fc964ff",
                    "masked_pan": "411111******1111",
                    "card_brand": "visa",
                    "expire_month": "12",
                    "expire_year": "2027",
                    "cardholder_name": null
                  }
                ]
              }
            }
        """.trimIndent()

        val card = gson.fromJson(json, SessionResponse::class.java).data!!.savedCards!!.single()

        assertNull(card.cardholderName)
        assertEquals("411111******1111", card.maskedPan)
    }

    @Test
    fun `status response parses saved_token`() {
        val json = """
            {
              "transaction_id": "550e8400-e29b-41d4-a716-446655440000",
              "status": "success",
              "amount": 9999,
              "currency": "EUR",
              "saved_token": "tok_abc123",
              "used_token": "6f9619ff-8b86-d011-b42d-00cf4fc964ff"
            }
        """.trimIndent()

        val response = gson.fromJson(json, StatusResponse::class.java)

        assertEquals("tok_abc123", response.savedToken)
    }

    @Test
    fun `status response without saved_token leaves the token null`() {
        val json = """
            {"transaction_id": "550e8400-e29b-41d4-a716-446655440000", "status": "success"}
        """.trimIndent()

        assertNull(gson.fromJson(json, StatusResponse::class.java).savedToken)
    }

    private fun minimalBrowserInfo() = BrowserInfo(
        userAgent = "ua",
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
