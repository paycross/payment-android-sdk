package com.paycross.sdk.internal.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import com.paycross.sdk.R
import com.paycross.sdk.internal.api.models.SavedCard
import com.paycross.sdk.internal.ui.MIN_TOUCH_TARGET
import com.paycross.sdk.internal.ui.TestTags
import com.paycross.sdk.internal.ui.rememberPublishTestTags
import com.paycross.sdk.internal.ui.pcStringResource
import com.paycross.sdk.internal.ui.theme.LocalIconTint

private const val UNKNOWN_BRAND = "unknown"

@Composable
internal fun SavedCardSelector(
    savedCards: List<SavedCard>,
    selectedCard: SavedCard?,
    modifier: Modifier = Modifier,
    allowRemoval: Boolean = false,
    removalEnabled: Boolean = true,
    onCardSelected: (SavedCard?) -> Unit,
    onCardRemoved: (String) -> Unit = {}
) {
    var pendingRemovalUuid by rememberSaveable { mutableStateOf<String?>(null) }

    Column(modifier = modifier.selectableGroup().testTag(TestTags.SAVED_CARDS)) {
        savedCards.forEach { card ->
            SavedCardRow(
                card = card,
                selected = card.uuid == selectedCard?.uuid,
                allowRemoval = allowRemoval,
                removalEnabled = removalEnabled,
                onSelect = { onCardSelected(card) },
                onRemoveClick = { pendingRemovalUuid = card.uuid }
            )
        }
        NewCardRow(selected = selectedCard == null, onSelect = { onCardSelected(null) })
    }

    savedCards.find { it.uuid == pendingRemovalUuid }?.let { card ->
        RemoveCardDialog(
            card = card,
            onConfirm = {
                pendingRemovalUuid = null
                onCardRemoved(card.uuid)
            },
            onDismiss = { pendingRemovalUuid = null }
        )
    }
}

@Composable
private fun SavedCardRow(
    card: SavedCard,
    selected: Boolean,
    allowRemoval: Boolean,
    removalEnabled: Boolean,
    onSelect: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(TestTags.savedCard(card.uuid))
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.weight(1f)) {
            Text(card.rowTitle())
            Text(
                pcStringResource(
                    R.string.paycross_saved_card_expires,
                    card.expireMonth,
                    card.expireYear.takeLast(2)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (allowRemoval) {
            val removeDescription =
                pcStringResource(R.string.paycross_remove_card, card.rowTitle())
            IconButton(
                onClick = onRemoveClick,
                enabled = removalEnabled,
                // Material's IconButton does not enforce the 48dp bounds that accessibility services read.
                modifier = Modifier
                    .testTag(TestTags.savedCardDelete(card.uuid))
                    .size(MIN_TOUCH_TARGET)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = removeDescription,
                    // Unspecified while disabled, so IconButton's greyed content colour applies.
                    tint = (if (removalEnabled) LocalIconTint.current else Color.Unspecified)
                        .takeOrElse { LocalContentColor.current }
                )
            }
        }
    }
}

@Composable
private fun NewCardRow(selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(TestTags.USE_NEW_CARD)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(pcStringResource(R.string.paycross_use_a_new_card))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun RemoveCardDialog(
    card: SavedCard,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = pcStringResource(R.string.paycross_remove_card_title)
    val publishTestTags = rememberPublishTestTags()
    AlertDialog(
        onDismissRequest = onDismiss,
        // The dialog is its own window, labelled by paneTitle: a contentDescription would merge its buttons.
        modifier = Modifier
            .testTag(TestTags.REMOVE_DIALOG)
            .semantics {
                paneTitle = title
                testTagsAsResourceId = publishTestTags
            },
        title = { Text(title) },
        text = {
            Text(pcStringResource(R.string.paycross_remove_card_message, card.rowTitle()))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(TestTags.REMOVE_CONFIRM)
            ) {
                Text(pcStringResource(R.string.paycross_remove_card_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TestTags.REMOVE_DISMISS)
            ) {
                Text(pcStringResource(R.string.paycross_remove_card_keep))
            }
        }
    )
}

private fun SavedCard.rowTitle(): String {
    val lastFour = "•••• ${maskedPan.takeLast(4)}"
    return displayBrand()?.let { "$it $lastFour" } ?: lastFour
}

// The BIN lookup returns the literal "unknown" for a brand it cannot name, which is dropped.
private fun SavedCard.displayBrand(): String? {
    val brand = cardBrand?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals(UNKNOWN_BRAND, ignoreCase = true)
    } ?: return null

    return brand.split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}
