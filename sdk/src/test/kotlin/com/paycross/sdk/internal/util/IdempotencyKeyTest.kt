package com.paycross.sdk.internal.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class IdempotencyKeyTest {

    @Test
    fun `generate returns valid UUID format`() {
        val key = IdempotencyKey.generate()
        // Should not throw - validates UUID format
        UUID.fromString(key)
    }

    @Test
    fun `generate returns unique keys`() {
        val keys = (1..100).map { IdempotencyKey.generate() }.toSet()
        assertEquals(100, keys.size)
    }
}
