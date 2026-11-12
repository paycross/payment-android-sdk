package com.paycross.sdk.internal.validation

import com.paycross.sdk.R
import com.paycross.sdk.internal.api.models.FieldDefinition
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.localizedLabel
import com.paycross.sdk.internal.api.models.localizedMessage
import com.paycross.sdk.internal.util.UiText

internal data class FieldState(
    val visible: Boolean,
    val required: Boolean,
    val readonly: Boolean
)

internal data class FieldGroupError(
    val groupKey: String,
    val fieldName: String,
    val message: UiText
)

internal object FieldGroupLogic {

    fun computeFieldState(field: FieldDefinition, groupValues: Map<String, String>): FieldState {
        val condition = field.condition
            ?: return FieldState(
                visible = true,
                required = field.required == true,
                readonly = field.readonly == true
            )

        val controlValue = groupValues[condition.whenField] ?: ""
        val conditionMet = condition.whenIn?.contains(controlValue) == true
        val effectiveDisplay = if (conditionMet) condition.display else condition.default

        return FieldState(
            visible = effectiveDisplay != "hidden",
            required = effectiveDisplay == "required",
            readonly = effectiveDisplay == "readonly"
        )
    }

    fun isDeclined(group: FieldGroup, optedInGroups: Set<String>): Boolean =
        group.optIn == true && group.key !in optedInGroups

    fun initialValues(groups: List<FieldGroup>): Map<String, Map<String, String>> {
        return groups.associate { group ->
            group.key to (group.fields.orEmpty()
                .filter { !it.value.isNullOrEmpty() }
                .associate { it.name to it.value!! })
        }.filterValues { it.isNotEmpty() }
    }

    fun validate(
        groups: List<FieldGroup>,
        values: Map<String, Map<String, String>>,
        language: String,
        optedInGroups: Set<String> = emptySet()
    ): List<FieldGroupError> {
        val errors = mutableListOf<FieldGroupError>()

        for (group in groups) {
            if (isDeclined(group, optedInGroups)) continue
            val groupValues = values[group.key].orEmpty()
            for (field in group.fields.orEmpty()) {
                val state = computeFieldState(field, groupValues)
                if (!state.visible) continue

                val value = groupValues[field.name].orEmpty()
                if (state.required && value.isBlank()) {
                    errors += FieldGroupError(
                        groupKey = group.key,
                        fieldName = field.name,
                        message = field.validation?.localizedMessage(language, "required")
                            ?.let(UiText::Raw)
                            ?: UiText.Resource(
                                R.string.paycross_field_required,
                                listOf(field.localizedLabel(language))
                            )
                    )
                    continue
                }

                // Capping silently truncates a pasted value, so the field blocks on submit instead.
                val maxLength = field.validation?.maxLength
                if (value.isNotBlank() && maxLength != null && value.length > maxLength) {
                    errors += FieldGroupError(
                        groupKey = group.key,
                        fieldName = field.name,
                        message = field.validation.localizedMessage(language, "max_length")
                            ?.let(UiText::Raw)
                            ?: UiText.Resource(
                                R.string.paycross_field_too_long,
                                listOf(field.localizedLabel(language), maxLength.toString())
                            )
                    )
                    continue
                }

                val pattern = field.validation?.pattern
                if (value.isNotBlank() && pattern != null && !Regex(pattern).containsMatchIn(value)) {
                    errors += FieldGroupError(
                        groupKey = group.key,
                        fieldName = field.name,
                        message = field.validation.localizedMessage(language, "pattern")
                            ?.let(UiText::Raw)
                            ?: UiText.Resource(
                                R.string.paycross_field_invalid,
                                listOf(field.localizedLabel(language))
                            )
                    )
                }
            }
        }

        return errors
    }

    fun submissionValues(
        groups: List<FieldGroup>,
        values: Map<String, Map<String, String>>,
        optedInGroups: Set<String> = emptySet()
    ): Map<String, Map<String, String>> {
        return groups.filterNot { isDeclined(it, optedInGroups) }.associate { group ->
            val groupValues = values[group.key].orEmpty()
            group.key to group.fields.orEmpty()
                .filter { computeFieldState(it, groupValues).visible }
                .mapNotNull { field ->
                    groupValues[field.name]?.takeIf { it.isNotBlank() }?.let { field.name to it }
                }
                .toMap()
        }.filterValues { it.isNotEmpty() }
    }
}
