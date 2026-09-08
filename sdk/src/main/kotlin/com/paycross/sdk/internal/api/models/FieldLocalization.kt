package com.paycross.sdk.internal.api.models

/** The group's label in [language], else the one the session was minted with. */
internal fun FieldGroup.localizedLabel(language: String): String? =
    labels?.get(language) ?: label

/** The field's label, falling back to its wire name so a field is never nameless. */
internal fun FieldDefinition.localizedLabel(language: String): String =
    labels?.get(language) ?: label ?: name

/** The field's placeholder, or null when it has none in any language. */
internal fun FieldDefinition.localizedPlaceholder(language: String): String? =
    placeholders?.get(language) ?: placeholder

/** A select option's label, falling back to the value that gets submitted. */
internal fun FieldOption.localizedLabel(language: String): String =
    labels?.get(language) ?: label ?: value

/** The merchant's own message for [rule], per rule, or null when they wrote none. */
internal fun FieldValidation.localizedMessage(language: String, rule: String): String? =
    messagesI18n?.get(language)?.get(rule) ?: messages?.get(rule)
