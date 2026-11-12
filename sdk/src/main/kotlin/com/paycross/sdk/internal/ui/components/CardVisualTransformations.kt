package com.paycross.sdk.internal.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private const val CARD_NUMBER_GROUP_SIZE = 4
private const val EXPIRY_MONTH_LENGTH = 2
private const val GROUP_SEPARATOR = " "
private const val EXPIRY_SEPARATOR = "/"

internal object CardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(
            AnnotatedString(groupCardNumber(text.text)),
            CardNumberOffsetMapping(text.text.length)
        )
}

internal object ExpiryVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(
            AnnotatedString(formatExpiry(text.text)),
            ExpiryOffsetMapping(text.text.length)
        )
}

internal fun groupCardNumber(digits: String): String =
    digits.chunked(CARD_NUMBER_GROUP_SIZE).joinToString(GROUP_SEPARATOR)

internal fun formatExpiry(digits: String): String =
    if (digits.length <= EXPIRY_MONTH_LENGTH) {
        digits
    } else {
        digits.substring(0, EXPIRY_MONTH_LENGTH) +
            EXPIRY_SEPARATOR +
            digits.substring(EXPIRY_MONTH_LENGTH)
    }

// WHY: Compose throws on an offset outside the mapped text, and a stale mapping is queried as the digit count changes.
internal class CardNumberOffsetMapping(private val digitCount: Int) : OffsetMapping {

    private val transformedLength = digitCount + separatorsBefore(digitCount)

    override fun originalToTransformed(offset: Int): Int {
        val digits = offset.coerceIn(0, digitCount)
        return digits + separatorsBefore(digits)
    }

    override fun transformedToOriginal(offset: Int): Int {
        val rendered = offset.coerceIn(0, transformedLength)
        val separators = rendered / (CARD_NUMBER_GROUP_SIZE + 1)
        return (rendered - separators).coerceIn(0, digitCount)
    }

    private fun separatorsBefore(digits: Int): Int =
        if (digits == 0) 0 else (digits - 1) / CARD_NUMBER_GROUP_SIZE
}

internal class ExpiryOffsetMapping(private val digitCount: Int) : OffsetMapping {

    private val hasSeparator = digitCount > EXPIRY_MONTH_LENGTH
    private val transformedLength = digitCount + if (hasSeparator) 1 else 0

    override fun originalToTransformed(offset: Int): Int {
        val digits = offset.coerceIn(0, digitCount)
        return if (hasSeparator && digits > EXPIRY_MONTH_LENGTH) digits + 1 else digits
    }

    override fun transformedToOriginal(offset: Int): Int {
        val rendered = offset.coerceIn(0, transformedLength)
        return if (hasSeparator && rendered > EXPIRY_MONTH_LENGTH) rendered - 1 else rendered
    }
}
