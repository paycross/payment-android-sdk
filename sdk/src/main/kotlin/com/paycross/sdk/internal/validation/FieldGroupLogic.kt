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

/**
 * Mirrors the checkout page's field-group semantics: a field's condition is
 * evaluated against sibling values in the same group, and only visible
 * required/max_length/pattern rules are enforced on submit.
 *
 * Errors name a string rather than carrying one: this is a plain object with no
 * Context, and the field's own server-supplied message - which is never ours to
 * translate - has to survive alongside the SDK's translated fallback.
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

    /**
     * Whether the shopper has left an opt-in group switched off. A group with no
     * opt-in flag is never declined, however [optedInGroups] is spelled, so a
     * caller that knows nothing about the toggle keeps today's behaviour.
     */
    fun isDeclined(group: FieldGroup, optedInGroups: Set<String>): Boolean =
        group.optIn == true && group.key !in optedInGroups

    fun initialValues(groups: List<FieldGroup>): Map<String, Map<String, String>> {
        return groups.associate { group ->
            group.key to (group.fields.orEmpty()
                .filter { !it.value.isNullOrEmpty() }
                .associate { it.name to it.value!! })
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * @param language The tag the sheet resolved. It picks which of the
     *   merchant's own messages and labels an error carries; the SDK's own
     *   fallbacks are resources and are translated by the resolved Resources
     *   instead. Still never a translation of the merchant's words: the backend
     *   supplied them, and one of the set is chosen rather than made.
     */
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

                // Blocked on submit rather than capped as it is typed. Capping
                // silently truncates a pasted value, and the page the merchant
                // already ships blocks — so this is the behaviour a shopper
                // meets on both surfaces.
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

    /**
     * Values to submit under `field_groups`: visible fields with non-blank
     * values, empty groups dropped.
     *
     * A declined opt-in group is dropped by name rather than by being empty.
     * The trailing filter would already drop one the shopper never typed into,
     * but not one the session prefilled - and a group the shopper declined must
     * not reach the wire with the merchant's own prefill standing in for a
     * choice they did not make.
     */
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
