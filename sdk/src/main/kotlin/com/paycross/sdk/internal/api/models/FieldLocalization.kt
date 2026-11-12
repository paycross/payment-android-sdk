package com.paycross.sdk.internal.api.models

internal fun FieldGroup.localizedLabel(language: String): String? =
    labels?.get(language) ?: label

internal fun FieldDefinition.localizedLabel(language: String): String =
    labels?.get(language) ?: label ?: name

internal fun FieldDefinition.localizedPlaceholder(language: String): String? =
    placeholders?.get(language) ?: placeholder

internal fun FieldOption.localizedLabel(language: String): String =
    labels?.get(language) ?: label ?: value

internal fun FieldValidation.localizedMessage(language: String, rule: String): String? =
    messagesI18n?.get(language)?.get(rule) ?: messages?.get(rule)
