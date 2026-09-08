package com.paycross.sdk.internal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.paycross.sdk.internal.api.models.FieldDefinition
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.localizedLabel
import com.paycross.sdk.internal.api.models.localizedPlaceholder
import com.paycross.sdk.internal.ui.LocalPayCrossLanguage
import com.paycross.sdk.internal.ui.TestTags
import com.paycross.sdk.internal.ui.pcStringResource
import com.paycross.sdk.internal.util.UiText
import com.paycross.sdk.internal.validation.FieldGroupLogic

/**
 * Renders merchant-configured field groups (customer/billing inputs) from
 * session data, honoring per-field conditions, prefill, and readonly state.
 */
@Composable
internal fun FieldGroupsSection(
    groups: List<FieldGroup>,
    values: Map<String, Map<String, String>>,
    errors: Map<String, UiText>,
    modifier: Modifier = Modifier,
    onValueChange: (group: String, field: String, value: String) -> Unit
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        groups.forEach { group ->
            FieldGroupCard(
                group = group,
                groupValues = values[group.key].orEmpty(),
                errors = errors,
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
    onValueChange: (field: String, value: String) -> Unit
) {
    val language = LocalPayCrossLanguage.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        group.localizedLabel(language)?.let {
            Text(text = it, style = MaterialTheme.typography.titleSmall)
        }

        group.fields.orEmpty().forEach { field ->
            val state = FieldGroupLogic.computeFieldState(field, groupValues)
            if (!state.visible) return@forEach

            val error = errors["${group.key}|${field.name}"]
            if (field.type == "select") {
                SelectField(
                    groupKey = group.key,
                    field = field,
                    value = groupValues[field.name].orEmpty(),
                    readonly = state.readonly,
                    error = error,
                    onValueChange = { onValueChange(field.name, it) }
                )
            } else {
                TextInputField(
                    groupKey = group.key,
                    field = field,
                    value = groupValues[field.name].orEmpty(),
                    readonly = state.readonly,
                    error = error,
                    onValueChange = { onValueChange(field.name, it) }
                )
            }
        }
    }
}

@Composable
private fun TextInputField(
    groupKey: String,
    field: FieldDefinition,
    value: String,
    readonly: Boolean,
    error: UiText?,
    onValueChange: (String) -> Unit
) {
    val language = LocalPayCrossLanguage.current

    PayCrossOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(field.localizedLabel(language)) },
        placeholder = field.localizedPlaceholder(language)?.let { { Text(it) } },
        readOnly = readonly,
        isError = error != null,
        supportingText = error?.let { { ErrorText(groupKey, field.name, it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardTypeFor(field.type)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TestTags.field(groupKey, field.name))
    )
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
    readonly: Boolean,
    error: UiText?,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val language = LocalPayCrossLanguage.current
    val options = field.options.orEmpty()
    val selectedLabel = options.find { it.value == value }?.localizedLabel(language) ?: value

    ExposedDropdownMenuBox(
        expanded = expanded && !readonly,
        onExpandedChange = { if (!readonly) expanded = it }
    ) {
        PayCrossOutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(field.localizedLabel(language)) },
            isError = error != null,
            supportingText = error?.let { { ErrorText(groupKey, field.name, it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .testTag(TestTags.field(groupKey, field.name))
        )
        ExposedDropdownMenu(
            expanded = expanded && !readonly,
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
