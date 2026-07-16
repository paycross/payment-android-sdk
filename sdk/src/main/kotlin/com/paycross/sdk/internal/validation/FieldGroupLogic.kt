package com.paycross.sdk.internal.validation

import com.paycross.sdk.internal.api.models.FieldDefinition
import com.paycross.sdk.internal.api.models.FieldGroup

internal data class FieldState(
    val visible: Boolean,
    val required: Boolean,
    val readonly: Boolean
)

internal data class FieldGroupError(
    val groupKey: String,
    val fieldName: String,
    val message: String
)

/**
 * Mirrors the checkout page's field-group semantics: a field's condition is
 * evaluated against sibling values in the same group, and only visible
 * required/pattern rules are enforced on submit.
 */
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

    fun initialValues(groups: List<FieldGroup>): Map<String, Map<String, String>> {
        return groups.associate { group ->
            group.key to (group.fields.orEmpty()
                .filter { !it.value.isNullOrEmpty() }
                .associate { it.name to it.value!! })
        }.filterValues { it.isNotEmpty() }
    }

    fun validate(
        groups: List<FieldGroup>,
        values: Map<String, Map<String, String>>
    ): List<FieldGroupError> {
        val errors = mutableListOf<FieldGroupError>()

        for (group in groups) {
            val groupValues = values[group.key].orEmpty()
            for (field in group.fields.orEmpty()) {
                val state = computeFieldState(field, groupValues)
                if (!state.visible) continue

                val value = groupValues[field.name].orEmpty()
                if (state.required && value.isBlank()) {
                    errors += FieldGroupError(
                        groupKey = group.key,
                        fieldName = field.name,
                        message = field.validation?.messages?.get("required")
                            ?: "${field.label ?: field.name} is required"
                    )
                    continue
                }

                val pattern = field.validation?.pattern
                if (value.isNotBlank() && pattern != null && !Regex(pattern).containsMatchIn(value)) {
                    errors += FieldGroupError(
                        groupKey = group.key,
                        fieldName = field.name,
                        message = field.validation.messages?.get("pattern")
                            ?: "${field.label ?: field.name} is invalid"
                    )
                }
            }
        }

        return errors
    }

    /**
     * Values to submit under `field_groups`: visible fields with non-blank
     * values, empty groups dropped.
     */
    fun submissionValues(
        groups: List<FieldGroup>,
        values: Map<String, Map<String, String>>
    ): Map<String, Map<String, String>> {
        return groups.associate { group ->
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
