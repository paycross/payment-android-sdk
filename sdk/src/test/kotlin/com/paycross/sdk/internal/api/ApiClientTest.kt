package com.paycross.sdk.internal.api

import com.paycross.sdk.PayCross
import com.paycross.sdk.PayCrossEnvironment
import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ApiClientTest {
    @Before
    fun setup() {
        PayCross.reset()
    }

    @After
    fun tearDown() {
        PayCross.reset()
    }

    @Test
    fun `get caches the api instance`() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
        assertSame(ApiClient.get(), ApiClient.get())
    }

    @Test
    fun `switching environment rebuilds the api instance`() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
        val staging = ApiClient.get()

        PayCross.init(environment = PayCrossEnvironment.PRODUCTION)

        assertNotSame(staging, ApiClient.get())
    }

    @Test
    fun `re-initializing the same environment keeps the cached instance`() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
        val first = ApiClient.get()

        PayCross.init(environment = PayCrossEnvironment.STAGING, brandColor = 0xFF00FF00.toInt())

        assertSame(first, ApiClient.get())
    }
}
