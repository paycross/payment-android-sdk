package com.paycross.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `init stores an appearance`() {
        val appearance = PayCrossAppearance(light = PayCrossColors(brand = 0xFF1E88E5.toInt()))
        PayCross.init(environment = PayCrossEnvironment.STAGING, appearance = appearance)
        assertEquals(appearance, PayCross.requireConfig().appearance)
        assertEquals(appearance, PayCross.requireConfig().effectiveAppearance())
    }

    @Test
    fun `a deprecated brandColor becomes an appearance`() {
        PayCross.init(environment = PayCrossEnvironment.STAGING, brandColor = 0xFF1E88E5.toInt())
        assertEquals(
            PayCrossAppearance.brand(0xFF1E88E5.toInt()),
            PayCross.requireConfig().effectiveAppearance()
        )
    }

    @Test
    fun `an appearance wins over a brandColor`() {
        val appearance = PayCrossAppearance(light = PayCrossColors(brand = 0xFF00A86B.toInt()))
        PayCross.init(
            environment = PayCrossEnvironment.STAGING,
            brandColor = 0xFF1E88E5.toInt(),
            appearance = appearance
        )
        assertEquals(appearance, PayCross.requireConfig().effectiveAppearance())
    }

    @Test
    fun `no colour of any kind leaves the appearance null`() {
        PayCross.init(environment = PayCrossEnvironment.STAGING)
        assertNull(PayCross.requireConfig().effectiveAppearance())
    }

    @Test
    fun `the brand factory fills both modes`() {
        val appearance = PayCrossAppearance.brand(0xFF1E88E5.toInt())
        assertEquals(0xFF1E88E5.toInt(), appearance.light?.brand)
        assertEquals(0xFF1E88E5.toInt(), appearance.dark?.brand)
        assertEquals(ThemeMode.SYSTEM, appearance.themeMode)
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

    @Test
    fun `test prefill is applied outside production`() {
        val prefill = TestCardPrefill(pan = "4111111111153220")
        PayCross.init(environment = PayCrossEnvironment.STAGING, testCardPrefill = prefill)
        assertEquals(prefill, PayCross.requireConfig().effectiveTestPrefill())
    }

    @Test
    fun `test prefill is ignored in production`() {
        PayCross.init(
            environment = PayCrossEnvironment.PRODUCTION,
            testCardPrefill = TestCardPrefill(pan = "4111111111153220")
        )
        assertEquals(null, PayCross.requireConfig().effectiveTestPrefill())
    }
}
