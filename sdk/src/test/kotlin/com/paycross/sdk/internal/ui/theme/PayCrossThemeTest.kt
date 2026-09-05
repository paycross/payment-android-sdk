package com.paycross.sdk.internal.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class PayCrossThemeTest {
    @Test
    fun `dark picks the dark scheme`() {
        assertEquals(darkColorScheme().background, payCrossColorScheme(dark = true, brand = null).background)
    }

    @Test
    fun `light picks the light scheme`() {
        assertEquals(lightColorScheme().background, payCrossColorScheme(dark = false, brand = null).background)
    }

    @Test
    fun `brand colour becomes primary in both modes`() {
        val brand = Color(0xFF00A86B)
        assertEquals(brand, payCrossColorScheme(dark = true, brand = brand).primary)
        assertEquals(brand, payCrossColorScheme(dark = false, brand = brand).primary)
    }

    @Test
    fun `light brand gets dark content`() {
        assertEquals(Color.Black, onBrandColor(Color(0xFFFFF176)))
    }

    @Test
    fun `dark brand gets light content`() {
        assertEquals(Color.White, onBrandColor(Color(0xFF0D47A1)))
    }

    @Test
    fun `mid grey brand gets dark content`() {
        assertEquals(Color.Black, onBrandColor(Color(0xFF808080)))
    }
}
