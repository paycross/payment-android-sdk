package com.paycross.sdk

import org.junit.Assert.*
import org.junit.Test

class PayCrossResultTest {
    @Test
    fun `Success contains transaction details`() {
        val result = PayCrossResult.Success(
            transactionId = "abc-123",
            status = "success",
            amount = 9999,
            currency = "EUR"
        )
        assertEquals("abc-123", result.transactionId)
        assertEquals("success", result.status)
        assertEquals(9999, result.amount)
        assertEquals("EUR", result.currency)
    }

    @Test
    fun `Failure contains recovery action`() {
        val result = PayCrossResult.Failure(
            transactionId = "abc-123",
            recovery = Recovery.CHANGE_METHOD
        )
        assertEquals("abc-123", result.transactionId)
        assertEquals(Recovery.CHANGE_METHOD, result.recovery)
    }

    @Test
    fun `Failure keeps the server's own recovery value`() {
        val result = PayCrossResult.Failure(
            transactionId = "abc-123",
            recovery = Recovery.UNRECOGNIZED,
            recoveryRaw = "issuer_wants_a_phone_call"
        )

        assertEquals("issuer_wants_a_phone_call", result.recoveryRaw)
    }

    @Test
    fun `a failure the SDK raised itself has no server value`() {
        val result = PayCrossResult.Failure(
            transactionId = null,
            recovery = Recovery.RESTART
        )

        assertNull(result.recoveryRaw)
    }

    @Test
    fun `Failure can have null transactionId`() {
        val result = PayCrossResult.Failure(
            transactionId = null,
            recovery = Recovery.RESTART
        )
        assertNull(result.transactionId)
    }

    @Test
    fun `Cancelled is singleton`() {
        val result = PayCrossResult.Cancelled
        assertTrue(result is PayCrossResult.Cancelled)
    }
}
