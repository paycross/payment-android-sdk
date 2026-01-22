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
import com.paycross.sdk.internal.ui.components.SavedCardSelector
import com.paycross.sdk.internal.validation.CardType
import com.paycross.sdk.internal.validation.CardValidator
import java.text.NumberFormat
import java.util.Currency

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
    onSubmit: (CardFormData) -> Unit
) {
    val brandColor = PayCross.requireConfig().brandColor?.let { Color(it) }
        ?: MaterialTheme.colorScheme.primary
    val savedCards = sessionData?.savedCards ?: emptyList()
    val canSaveCard = sessionData?.storedCredentials?.save != null

    var selectedCardUuid by rememberSaveable { mutableStateOf<String?>(null) }
    var cardNumber by rememberSaveable { mutableStateOf("") }
    var expiry by rememberSaveable { mutableStateOf("") }
    var cvv by rememberSaveable { mutableStateOf("") }
    var cardholderName by rememberSaveable { mutableStateOf("") }
    var saveCard by rememberSaveable { mutableStateOf(false) }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val selectedSavedCard by remember(selectedCardUuid, savedCards) {
        derivedStateOf { savedCards.find { it.uuid == selectedCardUuid } }
    }
    val isNewCard = selectedCardUuid == null
    val cardType = CardType.detect(cardNumber)
    val formattedAmount = formatAmount(claims.amount, claims.currency)

    val validation = validateForm(
        isNewCard = isNewCard,
        cardNumber = cardNumber,
        expiry = expiry,
        cvv = cvv,
        cardholderName = cardholderName,
        cardType = cardType
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        AmountHeader(amount = formattedAmount)

        if (savedCards.isNotEmpty()) {
            SavedCardSelector(
                savedCards = savedCards,
                selectedCard = selectedSavedCard,
                onCardSelected = { selectedCardUuid = it?.uuid }
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
                cvv = cvv,
                showErrors = showErrors,
                isCvvValid = validation.isCvvValid,
                onCvvChange = { cvv = it }
            )
        }

        error?.let { ErrorMessage(message = it) }

        Spacer(modifier = Modifier.weight(1f))

        PayButton(
            amount = formattedAmount,
            isLoading = isLoading,
            brandColor = brandColor,
            onClick = {
                showErrors = true
                if (validation.isValid) {
                    onSubmit(buildFormData(isNewCard, selectedSavedCard, cardNumber, expiry, cvv, cardholderName, saveCard))
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
    cvv: String,
    showErrors: Boolean,
    isCvvValid: Boolean,
    onCvvChange: (String) -> Unit
) {
    Text("Enter CVV for ${savedCard?.maskedPan}")
    CvvField(
        value = cvv,
        cardType = CardType.UNKNOWN,
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

private data class FormValidation(
    val isCardNumberValid: Boolean,
    val isExpiryValid: Boolean,
    val isCvvValid: Boolean,
    val isNameValid: Boolean
) {
    val isValid: Boolean
        get() = isCardNumberValid && isExpiryValid && isCvvValid && isNameValid
}

private fun validateForm(
    isNewCard: Boolean,
    cardNumber: String,
    expiry: String,
    cvv: String,
    cardholderName: String,
    cardType: CardType
): FormValidation {
    val isExpiryValid = !isNewCard || (expiry.length >= EXPIRY_MIN_LENGTH && CardValidator.isValidExpiry(
        expiry.substring(0, EXPIRY_MONTH_END),
        "$YEAR_PREFIX${expiry.substring(EXPIRY_MONTH_END)}"
    ))

    return FormValidation(
        isCardNumberValid = !isNewCard || CardValidator.isValidCardNumber(cardNumber),
        isExpiryValid = isExpiryValid,
        isCvvValid = CardValidator.isValidCvv(cvv, if (isNewCard) cardType else CardType.UNKNOWN),
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

private fun formatAmount(amount: Double, currency: String): String {
    val format = NumberFormat.getCurrencyInstance().apply {
        this.currency = Currency.getInstance(currency)
    }
    return format.format(amount)
}
