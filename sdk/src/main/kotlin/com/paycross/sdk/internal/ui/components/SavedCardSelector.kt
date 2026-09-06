package com.paycross.sdk.internal.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.paycross.sdk.R
import com.paycross.sdk.internal.api.models.SavedCard
import com.paycross.sdk.internal.ui.TestTags
import com.paycross.sdk.internal.ui.pcStringResource
import com.paycross.sdk.internal.ui.theme.LocalIconTint

private const val UNKNOWN_BRAND = "unknown"

/**
 * The stored-card picker: one selectable row per card, then "Use a new card".
 *
 * Rows rather than a dropdown because a per-row delete button and the dialog it
 * raises do not belong inside a menu that closes on the first touch, and because
 * iOS already draws the same list. Removal is a session-level merchant opt-in, so
 * [allowRemoval] gates the trailing icon; the dialog stands between it and
 * [onCardRemoved], since a delete cannot be undone from here.
 *
 * @param savedCards Most-recently-used first, as the session blob orders them.
 * @param selectedCard The card currently selected, or null for a new card.
 * @param allowRemoval Whether the session permits deleting a stored card.
 * @param removalEnabled Whether the delete buttons accept a tap. False while a
 * payment is in flight: the icons stay visible and greyed rather than vanishing,
 * so the row does not reflow under the shopper mid-authorization.
 * @param onCardSelected Called with the picked card, or null for "Use a new card".
 * @param onCardRemoved Called with a card's uuid once removal is confirmed.
 */
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
    // The uuid rather than the card: this survives rotation, and it resolves to
    // null once the card leaves the list, which closes the dialog on its own
    // after a removal completes.
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
                modifier = Modifier.testTag(TestTags.savedCardDelete(card.uuid))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = removeDescription,
                    // Unspecified while disabled, so the icon keeps the greyed
                    // content colour IconButton hands it rather than a merchant
                    // colour that would make a dead control look live.
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

@Composable
private fun RemoveCardDialog(
    card: SavedCard,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = pcStringResource(R.string.paycross_remove_card_title)
    AlertDialog(
        onDismissRequest = onDismiss,
        // paneTitle rather than a contentDescription: the dialog is a window of
        // its own, and a description here would merge the two buttons into one
        // unreadable node. TalkBack announces a pane by its title when it opens.
        modifier = Modifier
            .testTag(TestTags.REMOVE_DIALOG)
            .semantics { paneTitle = title },
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

/**
 * `card_brand` is the BIN lookup's value passed straight through, so one list can
 * hold "visa", "MASTERCARD" and the literal "unknown" at once. Title-casing gives
 * the rows one voice; an unknown brand drops out rather than being shown as a word.
 */
private fun SavedCard.displayBrand(): String? {
    val brand = cardBrand?.trim()?.takeIf {
        it.isNotEmpty() && !it.equals(UNKNOWN_BRAND, ignoreCase = true)
    } ?: return null

    return brand.split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}
