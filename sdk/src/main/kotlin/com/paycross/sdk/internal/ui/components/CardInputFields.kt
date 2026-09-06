package com.paycross.sdk.internal.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
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

@Composable
internal fun CardNumberField(
    value: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onValueChange: (String) -> Unit
) {
    PayCrossOutlinedTextField(
        // Raw digits in, grouping drawn on top: formatting the value itself
        // leaves the caret behind the group separator and the next keystroke
        // lands in front of the digit before it.
        value = value,
        onValueChange = { newValue ->
            val digitsOnly = newValue.filter { it.isDigit() }
            if (digitsOnly.length <= MAX_CARD_NUMBER_LENGTH) {
                onValueChange(digitsOnly)
            }
        },
        label = { Text("Card Number") },
        isError = isError,
        visualTransformation = CardNumberVisualTransformation,
        // NumberPassword, not Number: the framework treats the password variation
        // as a password input type, and EditorInfo then refuses to hand the field's
        // existing contents to the IME process as initial surrounding text.
        // Masking stays off — that is visualTransformation's job, not the keyboard's.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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
    PayCrossOutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val digitsOnly = newValue.filter { it.isDigit() }
            if (digitsOnly.length <= EXPIRY_LENGTH) {
                onValueChange(digitsOnly)
            }
        },
        label = { Text("MM/YY") },
        isError = isError,
        visualTransformation = ExpiryVisualTransformation,
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
    PayCrossOutlinedTextField(
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
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
    PayCrossOutlinedTextField(
        // The state is already uppercased on the way in; uppercasing it again on
        // the way out only risks the field editing text it never handed back.
        value = value,
        onValueChange = { onValueChange(it.uppercase()) },
        label = { Text("Cardholder Name") },
        isError = isError,
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "Cardholder name input" }
    )
}
