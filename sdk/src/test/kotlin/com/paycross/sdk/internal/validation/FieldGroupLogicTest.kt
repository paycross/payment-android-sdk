package com.paycross.sdk.internal.validation

import com.paycross.sdk.internal.api.models.FieldCondition
import com.paycross.sdk.internal.api.models.FieldDefinition
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.FieldValidation
import org.junit.Assert.*
import org.junit.Test

class FieldGroupLogicTest {

    private fun field(
        name: String,
        required: Boolean = false,
        readonly: Boolean = false,
        value: String? = null,
        condition: FieldCondition? = null,
        validation: FieldValidation? = null
    ) = FieldDefinition(
        name = name,
        type = "text",
        label = name,
        placeholder = null,
        required = required,
        readonly = readonly,
        value = value,
        condition = condition,
        options = null,
        validation = validation
    )

    @Test
    fun `unconditional field uses its own flags`() {
        val state = FieldGroupLogic.computeFieldState(field("line1", required = true), emptyMap())
        assertTrue(state.visible)
        assertTrue(state.required)
        assertFalse(state.readonly)
    }

    @Test
    fun `condition met applies display, not met applies default`() {
        val conditional = field(
            "state",
            condition = FieldCondition(whenField = "country", whenIn = listOf("US"), display = "required", default = "hidden")
        )

        val met = FieldGroupLogic.computeFieldState(conditional, mapOf("country" to "US"))
        assertTrue(met.visible)
        assertTrue(met.required)

        val notMet = FieldGroupLogic.computeFieldState(conditional, mapOf("country" to "DE"))
        assertFalse(notMet.visible)
    }

    @Test
    fun `validate reports missing required and bad pattern`() {
        val groups = listOf(
            FieldGroup(
                key = "billing_address",
                label = "Billing",
                fields = listOf(
                    field("line1", required = true),
                    field(
                        "country",
                        required = true,
                        validation = FieldValidation(pattern = "^[A-Z]{2}$", maxLength = 2, messages = mapOf("required" to "Country required"))
                    )
                )
            )
        )

        val errors = FieldGroupLogic.validate(
            groups,
            mapOf("billing_address" to mapOf("country" to "germany"))
        )

        assertEquals(2, errors.size)
        assertEquals("line1", errors[0].fieldName)
        assertEquals("country", errors[1].fieldName)
    }

    @Test
    fun `validate passes when values satisfy rules`() {
        val groups = listOf(
            FieldGroup(
                key = "billing_address",
                label = null,
                fields = listOf(
                    field("country", required = true, validation = FieldValidation("^[A-Z]{2}$", 2, null))
                )
            )
        )

        val errors = FieldGroupLogic.validate(groups, mapOf("billing_address" to mapOf("country" to "DE")))
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `hidden fields are not validated or submitted`() {
        val groups = listOf(
            FieldGroup(
                key = "billing_address",
                label = null,
                fields = listOf(
                    field(
                        "state",
                        condition = FieldCondition("country", listOf("US"), display = "required", default = "hidden")
                    ),
                    field("country")
                )
            )
        )
        val values = mapOf("billing_address" to mapOf("country" to "DE", "state" to "CA"))

        assertTrue(FieldGroupLogic.validate(groups, values).isEmpty())
        assertEquals(
            mapOf("billing_address" to mapOf("country" to "DE")),
            FieldGroupLogic.submissionValues(groups, values)
        )
    }

    @Test
    fun `initialValues picks prefilled fields only`() {
        val groups = listOf(
            FieldGroup(
                key = "customer_info",
                label = null,
                fields = listOf(field("email", value = "john@example.com"), field("phone"))
            )
        )

        assertEquals(
            mapOf("customer_info" to mapOf("email" to "john@example.com")),
            FieldGroupLogic.initialValues(groups)
        )
    }

    @Test
    fun `submissionValues drops empty groups and blank values`() {
        val groups = listOf(
            FieldGroup(key = "customer_info", label = null, fields = listOf(field("email"), field("phone")))
        )

        assertTrue(
            FieldGroupLogic.submissionValues(groups, mapOf("customer_info" to mapOf("email" to " "))).isEmpty()
        )
    }
}
