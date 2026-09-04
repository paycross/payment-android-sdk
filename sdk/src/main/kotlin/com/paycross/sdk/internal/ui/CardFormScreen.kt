package com.paycross.sdk.internal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.paycross.sdk.PayCross
import com.paycross.sdk.internal.api.JwtClaims
import com.paycross.sdk.internal.api.models.SavedCard
import com.paycross.sdk.internal.api.models.SessionData
import com.paycross.sdk.internal.ui.components.CardNumberField
import com.paycross.sdk.internal.ui.components.CardholderNameField
import com.paycross.sdk.internal.ui.components.CvvField
import com.paycross.sdk.internal.ui.components.ExpiryField
import com.paycross.sdk.internal.ui.components.FieldGroupsSection
import com.paycross.sdk.internal.ui.components.GooglePaySection
import com.paycross.sdk.internal.ui.components.SavedCardSelector
import com.paycross.sdk.internal.util.Amounts
import com.paycross.sdk.internal.validation.CardType
import com.paycross.sdk.internal.validation.CardValidator
import com.paycross.sdk.internal.validation.FieldGroupLogic
import com.paycross.sdk.internal.wallet.GooglePayRequests
import java.util.Locale

private const val EXPIRY_MIN_LENGTH = 4
private const val EXPIRY_MONTH_END = 2
private const val YEAR_PREFIX = "20"

/**
 * Card data submitted from the form.
 */
internal data class CardFormData(
    val cardholderName: String?,
    val pan: String?,
    val expireMonth: String?,
    val expireYear: String?,
    val cvv: String,
    val savedUuid: String?,
    val saveCard: Boolean
)

@Composable
internal fun CardFormScreen(
    claims: JwtClaims,
    sessionData: SessionData?,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    error: String? = null,
    googlePayAvailable: Boolean = false,
    onGooglePay: (Map<String, Map<String, String>>) -> Unit = {},
    onSubmit: (CardFormData, Map<String, Map<String, String>>) -> Unit
) {
    val brandColor = PayCross.requireConfig().brandColor?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primary
    val savedCards = sessionData?.savedCards ?: emptyList()
    val canSaveCard = sessionData?.saveCardConfig != null
    val fieldGroups = sessionData?.fieldGroups ?: emptyList()

    val prefill = PayCross.requireConfig().effectiveTestPrefill()
    var selectedCardUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var cardNumber by rememberSaveable { mutableStateOf(prefill?.pan.orEmpty()) }
    var expiry by rememberSaveable {
        mutableStateOf(prefill?.let { it.expireMonth + it.expireYear.takeLast(2) }.orEmpty())
    }
    // Deliberately not rememberSaveable: saved instance state is copied into
    // system_server, where the SDK cannot clear it, and survives process death —
    // the CVV would outlive authorization, which PCI DSS 3.3.1 forbids. The cost
    // is that rotation clears the CVV field and the shopper retypes it.
    var cvv by remember { mutableStateOf(prefill?.cvv.orEmpty()) }
    var cardholderName by rememberSaveable { mutableStateOf(prefill?.cardholderName.orEmpty()) }
    var saveCard by rememberSaveable { mutableStateOf(prefill?.saveCard ?: false) }
    var showErrors by rememberSaveable { mutableStateOf(false) }
    var fieldValuesFlat by rememberSaveable {
        mutableStateOf(flattenValues(FieldGroupLogic.initialValues(fieldGroups)))
    }

    val fieldValues = remember(fieldValuesFlat) { unflattenValues(fieldValuesFlat) }
    val selectedSavedCard by remember(selectedCardUuid, savedCards) {
        derivedStateOf { savedCards.find { it.uuid == selectedCardUuid } }
    }
    val isNewCard = selectedCardUuid == null
    val cardType = CardType.detect(cardNumber)
    val cvvCardType = cvvCardType(isNewCard, cardType, selectedSavedCard)
    val formattedAmount = formatAmount(claims, sessionData?.locale)

    val validation = validateForm(
        isNewCard = isNewCard,
        cardNumber = cardNumber,
        expiry = expiry,
        cvv = cvv,
        cardholderName = cardholderName,
        cvvCardType = cvvCardType
    )
    val fieldGroupErrors = remember(fieldGroups, fieldValuesFlat) {
        FieldGroupLogic.validate(fieldGroups, unflattenValues(fieldValuesFlat))
            .associate { "${it.groupKey}|${it.fieldName}" to it.message }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AmountHeader(amount = formattedAmount)

            if (googlePayAvailable) {
                GooglePaySection(
                    allowedPaymentMethodsJson = remember(claims, sessionData) {
                        GooglePayRequests.buildPaymentDataRequest(
                            claims,
                            sessionData,
                            PayCross.requireConfig().googlePayMerchantId
                        ).getAsJsonArray("allowedPaymentMethods").toString()
                    },
                    onClick = {
                        showErrors = true
                        // Field groups must validate before the sheet opens, like the
                        // web: core validates them unconditionally before the wallet
                        // branch, so an invalid form would open a sheet into a
                        // guaranteed reject. The isLoading check is the double-submit
                        // guard — the sheet closes on resolve while settlement is
                        // still polling and the button stays on screen.
                        if (!isLoading && fieldGroupErrors.isEmpty()) {
                            onGooglePay(FieldGroupLogic.submissionValues(fieldGroups, fieldValues))
                        }
                    }
                )
            }

            if (savedCards.isNotEmpty()) {
                SavedCardSelector(
                    savedCards = savedCards,
                    selectedCard = selectedSavedCard,
                    onCardSelected = { picked ->
                        cvv = cvvAfterCardSelection(selectedCardUuid, picked?.uuid, cvv)
                        selectedCardUuid = picked?.uuid
                    }
                )
            }

            if (isNewCard) {
                NewCardForm(
                    cardNumber = cardNumber,
                    expiry = expiry,
                    cvv = cvv,
                    cardholderName = cardholderName,
                    cardType = cardType,
                    saveCard = saveCard,
                    canSaveCard = canSaveCard,
                    showErrors = showErrors,
                    validation = validation,
                    onCardNumberChange = { cardNumber = it },
                    onExpiryChange = { expiry = it },
                    onCvvChange = { cvv = it },
                    onCardholderNameChange = { cardholderName = it },
                    onSaveCardChange = { saveCard = it }
                )
            } else {
                SavedCardCvvInput(
                    savedCard = selectedSavedCard,
                    cvvCardType = cvvCardType,
                    cvv = cvv,
                    showErrors = showErrors,
                    isCvvValid = validation.isCvvValid,
                    onCvvChange = { cvv = it }
                )
            }

            if (fieldGroups.isNotEmpty()) {
                FieldGroupsSection(
                    groups = fieldGroups,
                    values = fieldValues,
                    errors = if (showErrors) fieldGroupErrors else emptyMap(),
                    onValueChange = { group, field, value ->
                        fieldValuesFlat = HashMap(fieldValuesFlat).apply { put("$group|$field", value) }
                    }
                )
            }
        }

        error?.let { ErrorMessage(message = it) }

        Spacer(modifier = Modifier.height(16.dp))

        PayButton(
            amount = formattedAmount,
            isLoading = isLoading,
            brandColor = brandColor,
            onClick = {
                showErrors = true
                if (validation.isValid && fieldGroupErrors.isEmpty()) {
                    onSubmit(
                        buildFormData(isNewCard, selectedSavedCard, cardNumber, expiry, cvv, cardholderName, saveCard),
                        FieldGroupLogic.submissionValues(fieldGroups, fieldValues)
                    )
                    // Drop the form's reference once it is handed over; the CVV
                    // has no further use in the UI after submission.
                    cvv = ""
                }
            }
        )
    }
}

@Composable
private fun AmountHeader(amount: String) {
    Text(
        text = amount,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun NewCardForm(
    cardNumber: String,
    expiry: String,
    cvv: String,
    cardholderName: String,
    cardType: CardType,
    saveCard: Boolean,
    canSaveCard: Boolean,
    showErrors: Boolean,
    validation: FormValidation,
    onCardNumberChange: (String) -> Unit,
    onExpiryChange: (String) -> Unit,
    onCvvChange: (String) -> Unit,
    onCardholderNameChange: (String) -> Unit,
    onSaveCardChange: (Boolean) -> Unit
) {
    CardNumberField(
        value = cardNumber,
        isError = showErrors && !validation.isCardNumberValid,
        onValueChange = onCardNumberChange
    )

    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        ExpiryField(
            value = expiry,
            isError = showErrors && !validation.isExpiryValid,
            onValueChange = onExpiryChange,
            modifier = Modifier.weight(1f)
        )
        CvvField(
            value = cvv,
            cardType = cardType,
            isError = showErrors && !validation.isCvvValid,
            onValueChange = onCvvChange,
            modifier = Modifier.weight(1f)
        )
    }

    CardholderNameField(
        value = cardholderName,
        isError = showErrors && !validation.isNameValid,
        onValueChange = onCardholderNameChange
    )

    if (canSaveCard) {
        SaveCardCheckbox(checked = saveCard, onCheckedChange = onSaveCardChange)
    }
}

@Composable
private fun SaveCardCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text("Save card for future use")
    }
}

@Composable
private fun SavedCardCvvInput(
    savedCard: SavedCard?,
    cvvCardType: CardType,
    cvv: String,
    showErrors: Boolean,
    isCvvValid: Boolean,
    onCvvChange: (String) -> Unit
) {
    Text("Enter CVV for ${savedCard?.maskedPan}")
    CvvField(
        value = cvv,
        cardType = cvvCardType,
        isError = showErrors && !isCvvValid,
        onValueChange = onCvvChange,
        modifier = Modifier.width(100.dp)
    )
}

@Composable
private fun ErrorMessage(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun PayButton(
    amount: String,
    isLoading: Boolean,
    brandColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = brandColor),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color.White
            )
        } else {
            Text("Pay $amount")
        }
    }
}

internal data class FormValidation(
    val isCardNumberValid: Boolean,
    val isExpiryValid: Boolean,
    val isCvvValid: Boolean,
    val isNameValid: Boolean
) {
    val isValid: Boolean
        get() = isCardNumberValid && isExpiryValid && isCvvValid && isNameValid
}

/**
 * The card type whose CVV length governs the field and its validation. One
 * derivation feeds both, so the box cannot accept a digit the validator rejects.
 */
internal fun cvvCardType(
    isNewCard: Boolean,
    enteredCardType: CardType,
    savedCard: SavedCard?
): CardType = if (isNewCard) enteredCardType else CardType.fromBrand(savedCard?.cardBrand)

/**
 * The CVV the form keeps when the shopper picks a card. A CVV belongs to the card
 * it was typed for, so any change of card drops it: one form-level `cvv` backs both
 * entry modes, and carrying a value across would submit a new card's CVV against a
 * stored card's token while the "Enter CVV for <pan>" prompt sat over a box that
 * already looked filled. Re-picking the card already selected is not a change and
 * keeps what was typed. iOS drops it in CardFormState.sourceSelected for the same
 * reason.
 */
internal fun cvvAfterCardSelection(
    selectedUuid: String?,
    pickedUuid: String?,
    cvv: String
): String = if (selectedUuid == pickedUuid) cvv else ""

internal fun validateForm(
    isNewCard: Boolean,
    cardNumber: String,
    expiry: String,
    cvv: String,
    cardholderName: String,
    cvvCardType: CardType
): FormValidation {
    val isExpiryValid = !isNewCard || (expiry.length >= EXPIRY_MIN_LENGTH && CardValidator.isValidExpiry(
        expiry.substring(0, EXPIRY_MONTH_END),
        "$YEAR_PREFIX${expiry.substring(EXPIRY_MONTH_END)}"
    ))

    return FormValidation(
        isCardNumberValid = !isNewCard || CardValidator.isValidCardNumber(cardNumber),
        isExpiryValid = isExpiryValid,
        isCvvValid = CardValidator.isValidCvv(cvv, cvvCardType),
        isNameValid = !isNewCard || CardValidator.isValidCardholderName(cardholderName)
    )
}

private fun buildFormData(
    isNewCard: Boolean,
    savedCard: SavedCard?,
    cardNumber: String,
    expiry: String,
    cvv: String,
    cardholderName: String,
    saveCard: Boolean
): CardFormData {
    return if (isNewCard) {
        CardFormData(
            cardholderName = cardholderName,
            pan = cardNumber,
            expireMonth = expiry.substring(0, EXPIRY_MONTH_END),
            expireYear = "$YEAR_PREFIX${expiry.substring(EXPIRY_MONTH_END)}",
            cvv = cvv,
            savedUuid = null,
            saveCard = saveCard
        )
    } else {
        CardFormData(
            cardholderName = null,
            pan = null,
            expireMonth = null,
            expireYear = null,
            cvv = cvv,
            savedUuid = savedCard?.uuid,
            saveCard = false
        )
    }
}

private fun formatAmount(claims: JwtClaims, locale: String?): String {
    val displayLocale = locale?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
    return Amounts.formatMinor(claims.amount, claims.currency, displayLocale)
}

private fun flattenValues(values: Map<String, Map<String, String>>): HashMap<String, String> {
    val flat = HashMap<String, String>()
    values.forEach { (group, fields) ->
        fields.forEach { (field, value) -> flat["$group|$field"] = value }
    }
    return flat
}

private fun unflattenValues(flat: Map<String, String>): Map<String, Map<String, String>> {
    val nested = mutableMapOf<String, MutableMap<String, String>>()
    flat.forEach { (key, value) ->
        val group = key.substringBefore('|')
        val field = key.substringAfter('|')
        nested.getOrPut(group) { mutableMapOf() }[field] = value
    }
    return nested
}
