package com.paycross.sdk

import org.junit.Assert.*
import org.junit.Test

class RecoveryTest {

    @Test
    fun `known values map to their enum`() {
        assertEquals(Recovery.RETRY, Recovery.fromString("retry"))
        assertEquals(Recovery.CHANGE_METHOD, Recovery.fromString("change_method"))
        assertEquals(Recovery.RESTART, Recovery.fromString("restart"))
        assertEquals(Recovery.CONTACT_SUPPORT, Recovery.fromString("contact_support"))
        assertEquals(Recovery.DO_NOT_RETRY, Recovery.fromString("do_not_retry"))
    }

    @Test
    fun `legacy contact_us maps to contact_support`() {
        assertEquals(Recovery.CONTACT_SUPPORT, Recovery.fromString("contact_us"))
    }

    @Test
    fun `absent recovery defaults to retry`() {
        assertEquals(Recovery.RETRY, Recovery.fromString(null))
        assertEquals(Recovery.RETRY, Recovery.fromString(""))
        assertEquals(Recovery.RETRY, Recovery.fromString("  "))
    }

    @Test
    fun `unknown recovery fails closed`() {
        assertEquals(Recovery.DO_NOT_RETRY, Recovery.fromString("some_future_value"))
    }

    @Test
    fun `only retry and change_method are retryable`() {
        assertTrue(Recovery.RETRY.isRetryable)
        assertTrue(Recovery.CHANGE_METHOD.isRetryable)
        assertFalse(Recovery.RESTART.isRetryable)
        assertFalse(Recovery.CONTACT_SUPPORT.isRetryable)
        assertFalse(Recovery.DO_NOT_RETRY.isRetryable)
    }
}
