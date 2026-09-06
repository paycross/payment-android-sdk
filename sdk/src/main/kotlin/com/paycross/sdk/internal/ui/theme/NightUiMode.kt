package com.paycross.sdk.internal.ui.theme

import android.content.res.Configuration
import com.paycross.sdk.ThemeMode

/**
 * [uiMode] with its night bits set to what [mode] pins, or unchanged when the
 * mode follows the device.
 *
 * Every other bit is carried through: the same field also holds the UI type,
 * which says whether the sheet is on a watch, a TV or a car, and rewriting that
 * would hand the window the wrong resources entirely.
 *
 * The Configuration values are compile-time constants and inline, so this stays
 * a JVM unit test rather than an emulator one.
 */
internal fun nightUiMode(mode: ThemeMode, uiMode: Int): Int {
    val night = when (mode) {
        ThemeMode.SYSTEM -> return uiMode
        ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
        ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
    }
    return (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
}
