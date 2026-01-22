package com.paycross.sdk.internal.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.paycross.sdk.internal.validation.CardType

private const val MAX_CARD_NUMBER_LENGTH = 19
private const val EXPIRY_LENGTH = 4
private const val CARD_NUMBER_CHUNK_SIZE = 4
private const val EXPIRY_MONTH_LENGTH = 2

@Composable
internal fun CardNumberField(
    value: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = formatCardNumber(value),
        onValueChange = { newValue ->
            val digitsOnly = newValue.filter { it.isDigit() }
            if (digitsOnly.length <= MAX_CARD_NUMBER_LENGTH) {
                onValueChange(digitsOnly)
            }
        },
        label = { Text("Card Number") },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Card number input" }
    )
}

@Composable
internal fun ExpiryField(
    value: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = formatExpiry(value),
        onValueChange = { newValue ->
            val digitsOnly = newValue.filter { it.isDigit() }
            if (digitsOnly.length <= EXPIRY_LENGTH) {
                onValueChange(digitsOnly)
            }
        },
        label = { Text("MM/YY") },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.semantics { contentDescription = "Expiry date input" }
    )
}

@Composable
internal fun CvvField(
    value: String,
    cardType: CardType,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val digitsOnly = newValue.filter { it.isDigit() }
            if (digitsOnly.length <= cardType.cvvLength) {
                onValueChange(digitsOnly)
            }
        },
        label = { Text("CVV") },
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.semantics { contentDescription = "CVV input" }
    )
}

@Composable
internal fun CardholderNameField(
    value: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value.uppercase(),
        onValueChange = { onValueChange(it.uppercase()) },
        label = { Text("Cardholder Name") },
        isError = isError,
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Cardholder name input" }
    )
}

private fun formatCardNumber(input: String): String {
    return input.chunked(CARD_NUMBER_CHUNK_SIZE).joinToString(" ")
}

private fun formatExpiry(input: String): String {
    return when {
        input.length <= EXPIRY_MONTH_LENGTH -> input
        else -> "${input.substring(0, EXPIRY_MONTH_LENGTH)}/${input.substring(EXPIRY_MONTH_LENGTH)}"
    }
}
