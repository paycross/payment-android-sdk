package com.paycross.sdk.internal.ui.theme

import android.content.res.Configuration
import com.paycross.sdk.ThemeMode

// WHY: Only the night bits of uiMode are rewritten: the same field also holds the UI type (watch, TV, car).
internal fun nightUiMode(mode: ThemeMode, uiMode: Int): Int {
    val night = when (mode) {
        ThemeMode.SYSTEM -> return uiMode
        ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
        ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
    }
    return (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
}
