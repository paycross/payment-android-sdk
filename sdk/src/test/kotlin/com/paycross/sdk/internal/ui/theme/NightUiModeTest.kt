package com.paycross.sdk.internal.ui.theme

import android.content.res.Configuration
import com.paycross.sdk.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class NightUiModeTest {

    @Test
    fun `a pinned dark mode sets the night bits`() {
        assertEquals(
            Configuration.UI_MODE_NIGHT_YES,
            nightUiMode(ThemeMode.DARK, Configuration.UI_MODE_NIGHT_NO)
        )
    }

    @Test
    fun `a pinned light mode clears them`() {
        assertEquals(
            Configuration.UI_MODE_NIGHT_NO,
            nightUiMode(ThemeMode.LIGHT, Configuration.UI_MODE_NIGHT_YES)
        )
    }

    @Test
    fun `an undefined night mode is still pinned`() {
        assertEquals(
            Configuration.UI_MODE_NIGHT_YES,
            nightUiMode(ThemeMode.DARK, Configuration.UI_MODE_NIGHT_UNDEFINED)
        )
    }

    @Test
    fun `the system mode changes nothing`() {
        val uiMode = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_YES
        assertEquals(uiMode, nightUiMode(ThemeMode.SYSTEM, uiMode))
    }

    @Test
    fun `the UI type survives a pinned mode`() {
        // The same field says whether the sheet is on a watch, a TV or a car.
        // Clearing that would hand the window the wrong resources entirely.
        val onTv = Configuration.UI_MODE_TYPE_TELEVISION or Configuration.UI_MODE_NIGHT_NO

        val pinned = nightUiMode(ThemeMode.DARK, onTv)

        assertEquals(
            Configuration.UI_MODE_TYPE_TELEVISION,
            pinned and Configuration.UI_MODE_TYPE_MASK
        )
        assertEquals(Configuration.UI_MODE_NIGHT_YES, pinned and Configuration.UI_MODE_NIGHT_MASK)
    }
}
