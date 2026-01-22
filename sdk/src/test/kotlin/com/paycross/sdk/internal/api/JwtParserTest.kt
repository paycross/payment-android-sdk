package com.paycross.sdk.internal.api

import org.junit.Assert.*
import org.junit.Test

class JwtParserTest {
    // Test token payload (base64url): {"sub":"session-123","merchant":"merchant-456","amount":99.99,"currency":"EUR"}
    private val testToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzZXNzaW9uLTEyMyIsIm1lcmNoYW50IjoibWVyY2hhbnQtNDU2IiwiYW1vdW50Ijo5OS45OSwiY3VycmVuY3kiOiJFVVIifQ.signature"

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
    fun `parse extracts amount`() {
        val claims = JwtParser.parse(testToken)
        assertEquals(99.99, claims.amount, 0.01)
    }

    @Test
    fun `parse extracts currency`() {
        val claims = JwtParser.parse(testToken)
        assertEquals("EUR", claims.currency)
    }

    @Test
    fun `parse handles optional fields with defaults`() {
        // Token with only required fields: {"sub":"s1","amount":10.0,"currency":"USD"}
        val minimalToken = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJzMSIsImFtb3VudCI6MTAuMCwiY3VycmVuY3kiOiJVU0QifQ.sig"
        val claims = JwtParser.parse(minimalToken)
        assertEquals("s1", claims.sessionId)
        assertEquals("", claims.merchantId)
        assertEquals("", claims.customerId)
        assertNull(claims.brandingId)
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
