package com.paycross.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class PayCrossTest {
    @Before
    fun setup() {
        PayCross.reset() // Reset state between tests
    }

    @Test
    fun `init stores configuration`() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
        val config = PayCross.getConfigOrNull()
        assertNotNull(config)
        assertEquals(PayCrossEnvironment.STAGING, config?.environment)
    }

    @Test
    fun `init with brandColor stores color`() {
        PayCross.init(
            environment = PayCrossEnvironment.PRODUCTION,
            brandColor = 0xFF1E88E5.toInt()
        )
        val config = PayCross.getConfigOrNull()
        assertEquals(0xFF1E88E5.toInt(), config?.brandColor)
    }

    @Test(expected = IllegalStateException::class)
    fun `requireConfig throws if not initialized`() {
        PayCross.requireConfig()
    }

    @Test
    fun `requireConfig returns config after init`() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
        val config = PayCross.requireConfig()
        assertEquals(PayCrossEnvironment.STAGING, config.environment)
    }
}
