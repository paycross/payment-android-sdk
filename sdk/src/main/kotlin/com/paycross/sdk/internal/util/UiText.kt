package com.paycross.sdk.internal.util

import androidx.annotation.StringRes

internal sealed interface UiText {

    // Args are pre-rendered: Resources.getString formats numbers in the resources' locale, which the SDK clamps.
    data class Resource(@StringRes val id: Int, val args: List<String> = emptyList()) : UiText

    data class Raw(val text: String) : UiText
}
