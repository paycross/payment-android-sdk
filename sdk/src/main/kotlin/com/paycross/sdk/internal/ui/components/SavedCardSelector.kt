package com.paycross.sdk.internal.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.paycross.sdk.internal.api.models.SavedCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedCardSelector(
    savedCards: List<SavedCard>,
    selectedCard: SavedCard?,
    modifier: Modifier = Modifier,
    onCardSelected: (SavedCard?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.semantics { contentDescription = "Saved card selector" }
    ) {
        OutlinedTextField(
            value = selectedCard?.displayText() ?: "Use a new card",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            NewCardMenuItem(onSelect = {
                onCardSelected(null)
                expanded = false
            })
            savedCards.forEach { card ->
                SavedCardMenuItem(card = card, onSelect = {
                    onCardSelected(card)
                    expanded = false
                })
            }
        }
    }
}

@Composable
private fun NewCardMenuItem(onSelect: () -> Unit) {
    DropdownMenuItem(
        text = { Text("Use a new card") },
        onClick = onSelect
    )
}

@Composable
private fun SavedCardMenuItem(card: SavedCard, onSelect: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(card.maskedPan)
                Text(
                    "${card.cardholderName} - ${card.expireMonth}/${card.expireYear}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        onClick = onSelect
    )
}

private fun SavedCard.displayText(): String =
    "$maskedPan ($expireMonth/$expireYear)"
