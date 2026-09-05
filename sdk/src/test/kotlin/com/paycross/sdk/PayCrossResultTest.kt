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
    fun `Cancelled carries the last transaction the sheet knew about`() {
        val result = PayCrossResult.Cancelled(transactionId = "abc-123")

        assertEquals("abc-123", result.transactionId)
    }

    @Test
    fun `Cancelled can have null transactionId`() {
        // Cancelled before any transaction existed.
        assertNull(PayCrossResult.Cancelled(transactionId = null).transactionId)
    }

    @Test
    fun `Cancelled still matches as a type`() {
        // Merchants branch on the type; that must keep working.
        val result: PayCrossResult = PayCrossResult.Cancelled(transactionId = null)

        assertTrue(result is PayCrossResult.Cancelled)
    }

    @Test
    fun `Pending carries the transaction id and the reason`() {
        val result = PayCrossResult.Pending(
            transactionId = "abc-123",
            reason = PendingReason.POLL_TIMEOUT
        )

        assertEquals("abc-123", result.transactionId)
        assertEquals(PendingReason.POLL_TIMEOUT, result.reason)
    }

    @Test
    fun `Pending can have null transactionId`() {
        // Nothing to reconcile against, but the outcome is still unknown.
        assertNull(PayCrossResult.Pending(null, PendingReason.RESULT_LOST).transactionId)
    }

    @Test
    fun `Pending is not a Failure`() {
        // The whole point of the type: merchant code that branches on Failure
        // must not treat an unknown outcome as a decline.
        val result: PayCrossResult = PayCrossResult.Pending("abc-123", PendingReason.SERVER_VERIFY)

        assertTrue(result is PayCrossResult.Pending)
        assertFalse(result is PayCrossResult.Failure)
    }

    @Test
    fun `PendingReason wire names match iOS and the Flutter plugin`() {
        // These strings cross the platform boundary verbatim. Renaming a member
        // or reordering the enum silently changes what a host app receives.
        assertEquals(
            listOf("poll_timeout", "result_lost", "server_verify"),
            PendingReason.entries.map { it.name.lowercase() }
        )
    }
}
