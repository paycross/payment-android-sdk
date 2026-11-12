package com.paycross.sdk.internal.util

import java.util.UUID

internal object IdempotencyKey {
    fun generate(): String = UUID.randomUUID().toString()
}
