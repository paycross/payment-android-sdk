package com.paycross.sdk.internal.api.models

/**
 * The merchant's field-group copy in the language the sheet is drawing.
 *
 * Every rendered string in `field_groups` arrives twice: the singular key, in
 * whatever language the session was minted for, and beside it a map of the same
 * string in every language the backend holds a translation of. The map does not
 * depend on the session's own locale, so the sheet can draw the merchant's
 * labels in the language it resolved for its own chrome instead of the one the
 * session was created with — which is what a shopper whose device is French
 * reading a session minted in English used to get.
 *
 * `language` is never re-resolved here. It is the tag [LocaleResolution][com.paycross.sdk.internal.util.LocaleResolution]
 * already settled on for the SDK's own strings, so the merchant's words and ours
 * can never disagree about which language the sheet is in.
 *
 * Every read falls back to the singular key. A session minted before the backend
 * published the maps carries none of them, and one carrying a language the
 * merchant has no translation for is the same case: the label the session came
 * with is still a label, and losing it would leave the field named after its
 * wire key.
 */
internal fun FieldGroup.localizedLabel(language: String): String? =
    labels?.get(language) ?: label

/** The field's label, falling back to its wire name so a field is never nameless. */
internal fun FieldDefinition.localizedLabel(language: String): String =
    labels?.get(language) ?: label ?: name

internal fun FieldDefinition.localizedPlaceholder(language: String): String? =
    placeholders?.get(language) ?: placeholder

/** A select option's label, falling back to the value that gets submitted. */
internal fun FieldOption.localizedLabel(language: String): String =
    labels?.get(language) ?: label ?: value

/** The merchant's own message for [rule], or null when they wrote none. */
internal fun FieldValidation.localizedMessage(language: String, rule: String): String? =
    messagesI18n?.get(language)?.get(rule) ?: messages?.get(rule)
