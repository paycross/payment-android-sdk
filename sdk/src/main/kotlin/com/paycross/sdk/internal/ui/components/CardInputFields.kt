package com.paycross.sdk.internal.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.paycross.sdk.R
import com.paycross.sdk.internal.ui.TestTags
import com.paycross.sdk.internal.ui.pcStringResource
import com.paycross.sdk.internal.validation.CardType

private const val MAX_CARD_NUMBER_LENGTH = 19
private const val EXPIRY_LENGTH = 4

/**
 * The spoken label, the identifier, and what is wrong with the field, on the
 * node that merges it.
 *
 * The merge is stated here rather than left to [PayCrossOutlinedTextField]'s
 * label branch because this is where it matters: a contentDescription on an
 * unmerged field would speak instead of the label and the error the decoration
 * box sets, not alongside them.
 *
 * [message] is set here rather than left to [PayCrossOutlinedTextField], whose
 * `error` is Material's generic sentence: peer semantics on a node collapse
 * outermost-first, so the specific message only survives if it is attached on
 * the modifier passed in. The merchant field inputs carry the same note.
 */
private fun Modifier.fieldSemantics(description: String, tag: String, message: String?): Modifier =
    this.testTag(tag).semantics(mergeDescendants = true) {
        contentDescription = description
        if (message != null) error(message)
    }

/**
 * The message under a card field. Tagged so it keeps a resource id of its own in
 * a device dump, where it sits inside the field's merged node and would
 * otherwise be unaddressable.
 */
@Composable
private fun CardErrorText(tag: String, message: String) {
    Text(text = message, modifier = Modifier.testTag(TestTags.errorFor(tag)))
}

@Composable
internal fun CardNumberField(
    value: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    onValueChange: (String) -> Unit
) {
    val description = pcStringResource(R.string.paycross_card_number_field)
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
        label = { Text(pcStringResource(R.string.paycross_card_number)) },
        isError = error != null,
        supportingText = error?.let { { CardErrorText(TestTags.CARD_NUMBER, it) } },
        visualTransformation = CardNumberVisualTransformation,
        // NumberPassword, not Number: the framework treats the password variation
        // as a password input type, and EditorInfo then refuses to hand the field's
        // existing contents to the IME process as initial surrounding text.
        // Masking stays off — that is visualTransformation's job, not the keyboard's.
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .fieldSemantics(description, TestTags.CARD_NUMBER, error)
    )
}

@Composable
internal fun ExpiryField(
    value: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    onValueChange: (String) -> Unit
) {
    val description = pcStringResource(R.string.paycross_expiry_field)
    PayCrossOutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val digitsOnly = newValue.filter { it.isDigit() }
            if (digitsOnly.length <= EXPIRY_LENGTH) {
                onValueChange(digitsOnly)
            }
        },
        label = { Text(pcStringResource(R.string.paycross_expiry_label)) },
        isError = error != null,
        supportingText = error?.let { { CardErrorText(TestTags.EXPIRY, it) } },
        visualTransformation = ExpiryVisualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier.fieldSemantics(description, TestTags.EXPIRY, error)
    )
}

@Composable
internal fun CvvField(
    value: String,
    cardType: CardType,
    modifier: Modifier = Modifier,
    error: String? = null,
    onValueChange: (String) -> Unit
) {
    val description = pcStringResource(R.string.paycross_cvv_field)
    PayCrossOutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val digitsOnly = newValue.filter { it.isDigit() }
            if (digitsOnly.length <= cardType.cvvLength) {
                onValueChange(digitsOnly)
            }
        },
        label = { Text(pcStringResource(R.string.paycross_cvv)) },
        isError = error != null,
        supportingText = error?.let { { CardErrorText(TestTags.CVV, it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        modifier = modifier.fieldSemantics(description, TestTags.CVV, error)
    )
}

@Composable
internal fun CardholderNameField(
    value: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    onValueChange: (String) -> Unit
) {
    val description = pcStringResource(R.string.paycross_cardholder_name_field)
    PayCrossOutlinedTextField(
        // The state is already uppercased on the way in; uppercasing it again on
        // the way out only risks the field editing text it never handed back.
        value = value,
        onValueChange = { onValueChange(it.uppercase()) },
        label = { Text(pcStringResource(R.string.paycross_cardholder_name)) },
        isError = error != null,
        supportingText = error?.let { { CardErrorText(TestTags.CARDHOLDER_NAME, it) } },
        singleLine = true,
        modifier = modifier
            .fillMaxWidth()
            .fieldSemantics(description, TestTags.CARDHOLDER_NAME, error)
    )
}
