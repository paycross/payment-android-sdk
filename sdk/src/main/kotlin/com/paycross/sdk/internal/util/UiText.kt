package com.paycross.sdk.internal.util

import androidx.annotation.StringRes

/**
 * A message on its way from somewhere with no Context to the place that draws it.
 *
 * The view model and the field-group validator both produce shopper-facing text
 * and neither can reach resources, so they name the string instead of building
 * it: [Resource] is the SDK's own copy and is translated where it is resolved,
 * while [Raw] is a sentence the server sent, already in whatever language the
 * server chose and never ours to translate.
 */
internal sealed interface UiText {

    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    data class Raw(val text: String) : UiText
}
