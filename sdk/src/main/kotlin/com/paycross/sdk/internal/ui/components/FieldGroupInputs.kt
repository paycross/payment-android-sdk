package com.paycross.sdk.internal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.paycross.sdk.R
import com.paycross.sdk.internal.api.models.FieldDefinition
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.localizedLabel
import com.paycross.sdk.internal.api.models.localizedPlaceholder
import com.paycross.sdk.internal.ui.LocalPayCrossLanguage
import com.paycross.sdk.internal.ui.MIN_TOUCH_TARGET
import com.paycross.sdk.internal.ui.TestTags
import com.paycross.sdk.internal.ui.pcStringResource
import com.paycross.sdk.internal.util.UiText
import com.paycross.sdk.internal.validation.FieldGroupLogic
import com.paycross.sdk.internal.validation.FieldState

@Composable
internal fun FieldGroupsSection(
    groups: List<FieldGroup>,
    values: Map<String, Map<String, String>>,
    errors: Map<String, UiText>,
    optedInGroups: Set<String>,
    onOptInChange: (group: String, optedIn: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onValueChange: (group: String, field: String, value: String) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        groups.forEach { group ->
            FieldGroupCard(
                group = group,
                groupValues = values[group.key].orEmpty(),
                errors = errors,
                optedIn = group.key in optedInGroups,
                onOptInChange = { onOptInChange(group.key, it) },
                onValueChange = { field, value -> onValueChange(group.key, field, value) }
            )
        }
    }
}

@Composable
private fun FieldGroupCard(
    group: FieldGroup,
    groupValues: Map<String, String>,
    errors: Map<String, UiText>,
    optedIn: Boolean,
    onOptInChange: (Boolean) -> Unit,
    onValueChange: (field: String, value: String) -> Unit
) {
    val language = LocalPayCrossLanguage.current
    val isOptIn = group.optIn == true
    val fields = if (isOptIn && !optedIn) emptyList() else group.fields.orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (isOptIn) {
            OptInToggle(
                groupKey = group.key,
                caption = group.localizedLabel(language) ?: group.key,
                checked = optedIn,
                onCheckedChange = onOptInChange
            )
        } else {
            group.localizedLabel(language)?.let {
                Text(text = it, style = MaterialTheme.typography.titleSmall)
            }
        }

        fields.forEach { field ->
            val state = FieldGroupLogic.computeFieldState(field, groupValues)
            if (!state.visible) return@forEach

            val error = errors["${group.key}|${field.name}"]
            if (field.type == "select") {
                SelectField(
                    groupKey = group.key,
                    field = field,
                    value = groupValues[field.name].orEmpty(),
                    state = state,
                    error = error,
                    onValueChange = { onValueChange(field.name, it) }
                )
            } else {
                TextInputField(
                    groupKey = group.key,
                    field = field,
                    value = groupValues[field.name].orEmpty(),
                    state = state,
                    error = error,
                    onValueChange = { onValueChange(field.name, it) }
                )
            }
        }
    }
}

@Composable
private fun OptInToggle(
    groupKey: String,
    caption: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MIN_TOUCH_TARGET)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .testTag(TestTags.groupOptIn(groupKey))
    ) {
        Switch(checked = checked, onCheckedChange = null)
        Text(text = caption, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun TextInputField(
    groupKey: String,
    field: FieldDefinition,
    value: String,
    state: FieldState,
    error: UiText?,
    onValueChange: (String) -> Unit
) {
    val language = LocalPayCrossLanguage.current
    val label = field.localizedLabel(language)

    PayCrossOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(marked(label, state.required)) },
        placeholder = if (state.readonly) null else field.localizedPlaceholder(language)?.let { { Text(it) } },
        readOnly = state.readonly,
        locked = state.readonly,
        isError = error != null,
        supportingText = error?.let { { ErrorText(groupKey, field.name, it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardTypeFor(field.type)),
        modifier = Modifier
            .fillMaxWidth()
            .merchantFieldSemantics(
                tag = TestTags.field(groupKey, field.name),
                name = label,
                requiredState = requiredState(state.required),
                message = error?.let { pcStringResource(it) }
            )
    )
}

private fun marked(label: String, required: Boolean): String =
    if (required) "$label *" else label

@Composable
private fun requiredState(required: Boolean): String? =
    if (required) pcStringResource(R.string.paycross_field_required_state) else null

private fun Modifier.merchantFieldSemantics(
    tag: String,
    name: String,
    requiredState: String?,
    message: String?
): Modifier = this
    .testTag(tag)
    .semantics(mergeDescendants = true) {
        contentDescription = name
        if (requiredState != null) stateDescription = requiredState
        if (message != null) error(message)
    }

@Composable
private fun ErrorText(groupKey: String, fieldName: String, error: UiText) {
    Text(
        text = pcStringResource(error),
        modifier = Modifier.testTag(TestTags.fieldError(groupKey, fieldName))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectField(
    groupKey: String,
    field: FieldDefinition,
    value: String,
    state: FieldState,
    error: UiText?,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val language = LocalPayCrossLanguage.current
    val label = field.localizedLabel(language)
    val options = field.options.orEmpty()
    val chosenLabel = options.find { it.value == value }?.localizedLabel(language) ?: value
    val prompt = field.localizedPlaceholder(language)
    val showsPrompt = chosenLabel.isEmpty() && !state.readonly && !prompt.isNullOrEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded && !state.readonly,
        onExpandedChange = { if (!state.readonly) expanded = it }
    ) {
        PayCrossOutlinedTextField(
            // Material shows a placeholder only when focused and empty; a select is never both.
            value = if (showsPrompt) prompt.orEmpty() else chosenLabel,
            valueIsPrompt = showsPrompt,
            onValueChange = {},
            readOnly = true,
            locked = state.readonly,
            label = { Text(marked(label, state.required)) },
            isError = error != null,
            supportingText = error?.let { { ErrorText(groupKey, field.name, it) } },
            trailingIcon = if (state.readonly) {
                null
            } else {
                { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .merchantFieldSemantics(
                    tag = TestTags.field(groupKey, field.name),
                    name = label,
                    requiredState = requiredState(state.required),
                    message = error?.let { pcStringResource(it) }
                )
        )
        ExposedDropdownMenu(
            expanded = expanded && !state.readonly,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.localizedLabel(language)) },
                    onClick = {
                        onValueChange(option.value)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun keyboardTypeFor(type: String?): KeyboardType = when (type) {
    "email" -> KeyboardType.Email
    "tel" -> KeyboardType.Phone
    else -> KeyboardType.Text
}
