package com.paycross.sdk.internal.api

import org.junit.Assert.*
import org.junit.Test

class JwtParserTest {
    // Payload: {"sub":"session-123","merchant":"merchant-456","amount":9999,"currency":"EUR","exp":4102444800}
    private val testToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzZXNzaW9uLTEyMyIsIm1lcmNoYW50IjoibWVyY2hhbnQtNDU2IiwiYW1vdW50Ijo5OTk5LCJjdXJyZW5jeSI6IkVVUiIsImV4cCI6NDEwMjQ0NDgwMH0.signature" // gitleaks:allow

    @Test
    fun `parse extracts session ID from sub claim`() {
        val claims = JwtParser.parse(testToken)
        assertEquals("session-123", claims.sessionId)
    }

    @Test
    fun `parse extracts merchant ID`() {
        val claims = JwtParser.parse(testToken)
        assertEquals("merchant-456", claims.merchantId)
    }

    @Test
    fun `parse extracts amount in minor units`() {
        val claims = JwtParser.parse(testToken)
        assertEquals(9999L, claims.amount)
    }

    @Test
    fun `parse extracts currency`() {
        val claims = JwtParser.parse(testToken)
        assertEquals("EUR", claims.currency)
    }

    @Test
    fun `parse extracts expiry`() {
        val claims = JwtParser.parse(testToken)
        assertEquals(4102444800L, claims.expiresAt)
        assertFalse(claims.isExpired())
    }

    @Test
    fun `parse handles optional fields with defaults`() {
        // Payload: {"sub":"s1","amount":1000,"currency":"USD"}
        val minimalToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzMSIsImFtb3VudCI6MTAwMCwiY3VycmVuY3kiOiJVU0QifQ.sig" // gitleaks:allow
        val claims = JwtParser.parse(minimalToken)
        assertEquals("s1", claims.sessionId)
        assertEquals("", claims.merchantId)
        assertEquals("", claims.customerId)
        assertNull(claims.brandingId)
        assertNull(claims.expiresAt)
        assertFalse(claims.isExpired())
    }

    @Test
    fun `expired token reports expired`() {
        // Payload: {"sub":"s2","amount":1000,"currency":"USD","exp":1000000000}
        val expiredToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzMiIsImFtb3VudCI6MTAwMCwiY3VycmVuY3kiOiJVU0QiLCJleHAiOjEwMDAwMDAwMDB9.sig" // gitleaks:allow
        val claims = JwtParser.parse(expiredToken)
        assertTrue(claims.isExpired())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on invalid token format`() {
        JwtParser.parse("not-a-jwt")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on malformed payload`() {
        JwtParser.parse("header.!!!invalid-base64!!!.signature")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on empty token`() {
        JwtParser.parse("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse throws on token with only two parts`() {
        JwtParser.parse("header.payload")
    }
}
