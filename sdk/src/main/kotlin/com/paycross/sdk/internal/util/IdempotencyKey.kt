package com.paycross.sdk.internal.util

import java.util.UUID

/**
 * Generates idempotency keys for payment operations.
 *
 * Idempotency keys ensure that payment requests can be safely retried
 * without causing duplicate transactions.
 */
internal object IdempotencyKey {

    /**
     * Generates a new unique idempotency key.
     *
     * @return A UUID v4 string suitable for use as an idempotency key.
     */
    fun generate(): String = UUID.randomUUID().toString()
}
