package com.paycross.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class PayCrossEnvironmentTest {
    @Test
    fun `STAGING has correct base URL`() {
        assertEquals("https://checkout.test-pay-cross.com/api", PayCrossEnvironment.STAGING.baseUrl)
    }

    @Test
    fun `PRODUCTION has correct base URL`() {
        assertEquals("https://checkout.pay-cross.com/api", PayCrossEnvironment.PRODUCTION.baseUrl)
    }
}
