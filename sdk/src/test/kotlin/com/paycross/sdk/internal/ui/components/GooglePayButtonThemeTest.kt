package com.paycross.sdk.internal.ui.components

import com.google.android.gms.wallet.button.ButtonConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class GooglePayButtonThemeTest {
    @Test
    fun `a dark sheet gets the light button`() {
        assertEquals(ButtonConstants.ButtonTheme.LIGHT, googlePayButtonTheme(dark = true))
    }

    @Test
    fun `a light sheet gets the dark button`() {
        assertEquals(ButtonConstants.ButtonTheme.DARK, googlePayButtonTheme(dark = false))
    }
}
