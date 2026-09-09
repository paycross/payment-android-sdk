package com.paycross.sdk.internal.validation

import com.paycross.sdk.R
import com.paycross.sdk.internal.api.models.FieldCondition
import com.paycross.sdk.internal.api.models.FieldDefinition
import com.paycross.sdk.internal.api.models.FieldGroup
import com.paycross.sdk.internal.api.models.FieldValidation
import com.paycross.sdk.internal.util.UiText
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
            mapOf("billing_address" to mapOf("country" to "de")),
            language = "en"
        )

        assertEquals(2, errors.size)
        assertEquals("line1", errors[0].fieldName)
        assertEquals("country", errors[1].fieldName)
    }

    @Test
    fun `a field with no server message names the SDK's own string`() {
        val groups = listOf(
            FieldGroup(
                key = "billing_address",
                label = null,
                fields = listOf(
                    field("line1", required = true),
                    field(
                        "post_code",
                        validation = FieldValidation(pattern = "^[0-9]+$", maxLength = 8, messages = null)
                    )
                )
            )
        )

        val errors = FieldGroupLogic.validate(
            groups,
            mapOf("billing_address" to mapOf("post_code" to "abc")),
            language = "en"
        )

        // The label is a format argument, so the sentence can be built the other
        // way round in another language.
        assertEquals(
            UiText.Resource(R.string.paycross_field_required, listOf("line1")),
            errors[0].message
        )
        assertEquals(
            UiText.Resource(R.string.paycross_field_invalid, listOf("post_code")),
            errors[1].message
        )
    }

    @Test
    fun `a server-supplied message is passed through untranslated`() {
        val groups = listOf(
            FieldGroup(
                key = "billing_address",
                label = null,
                fields = listOf(
                    field(
                        "country",
                        required = true,
                        validation = FieldValidation(
                            pattern = "^[A-Z]{2}$",
                            maxLength = 2,
                            messages = mapOf(
                                "required" to "Pays obligatoire",
                                "pattern" to "Code pays sur deux lettres"
                            )
                        )
                    )
                )
            )
        )

        val missing = FieldGroupLogic.validate(groups, emptyMap(), language = "en")
        assertEquals(UiText.Raw("Pays obligatoire"), missing.single().message)

        val badPattern = FieldGroupLogic.validate(
            groups,
            mapOf("billing_address" to mapOf("country" to "de")),
            language = "en"
        )
        assertEquals(UiText.Raw("Code pays sur deux lettres"), badPattern.single().message)
    }

    @Test
    fun `a server-supplied message is chosen in the sheet's language`() {
        val groups = listOf(
            FieldGroup(
                key = "billing_address",
                label = null,
                fields = listOf(
                    field(
                        "country",
                        required = true,
                        validation = FieldValidation(
                            pattern = null,
                            maxLength = 2,
                            messages = mapOf("required" to "Country required"),
                            messagesI18n = mapOf(
                                "en" to mapOf("required" to "Country required"),
                                "fr" to mapOf("required" to "Pays obligatoire")
                            )
                        )
                    )
                )
            )
        )

        assertEquals(
            UiText.Raw("Pays obligatoire"),
            FieldGroupLogic.validate(groups, emptyMap(), language = "fr").single().message
        )
        assertEquals(
            UiText.Raw("Country required"),
            FieldGroupLogic.validate(groups, emptyMap(), language = "en").single().message
        )
        // A language the merchant has no translation for keeps the message the
        // session was minted with rather than losing it.
        assertEquals(
            UiText.Raw("Country required"),
            FieldGroupLogic.validate(groups, emptyMap(), language = "de").single().message
        )
    }

    @Test
    fun `the SDK's own message names the field in the sheet's language`() {
        // The message is a resource and the resolved Resources translate it; the
        // argument is the merchant's label and has to be picked, not translated.
        val groups = listOf(
            FieldGroup(
                key = "customer_info",
                label = null,
                fields = listOf(
                    field("email", required = true).copy(
                        labels = mapOf("en" to "Email address", "fr" to "Adresse e-mail")
                    )
                )
            )
        )

        assertEquals(
            UiText.Resource(R.string.paycross_field_required, listOf("Adresse e-mail")),
            FieldGroupLogic.validate(groups, emptyMap(), language = "fr").single().message
        )
        assertEquals(
            UiText.Resource(R.string.paycross_field_required, listOf("Email address")),
            FieldGroupLogic.validate(groups, emptyMap(), language = "en").single().message
        )
    }

    @Test
    fun `a field with no label falls back to its wire name`() {
        val unlabelled = FieldDefinition(
            name = "vat_id",
            type = "text",
            label = null,
            placeholder = null,
            required = true,
            readonly = false,
            value = null,
            condition = null,
            options = null,
            validation = null
        )
        val groups = listOf(FieldGroup(key = "billing_address", label = null, fields = listOf(unlabelled)))

        assertEquals(
            UiText.Resource(R.string.paycross_field_required, listOf("vat_id")),
            FieldGroupLogic.validate(groups, emptyMap(), language = "en").single().message
        )
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

        val errors = FieldGroupLogic.validate(
            groups,
            mapOf("billing_address" to mapOf("country" to "DE")),
            language = "en"
        )
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

        assertTrue(FieldGroupLogic.validate(groups, values, language = "en").isEmpty())
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

    @Test
    fun `a value at the max_length passes and one over it carries the merchant's message`() {
        val groups = listOf(
            FieldGroup(
                key = "customer_info",
                label = null,
                fields = listOf(
                    field(
                        "email",
                        validation = FieldValidation(
                            pattern = null,
                            maxLength = 254,
                            messages = mapOf("max_length" to "Maximum 254 characters"),
                            messagesI18n = mapOf(
                                "en" to mapOf("max_length" to "Maximum 254 characters"),
                                "fr" to mapOf("max_length" to "Maximum 254 caractères")
                            )
                        )
                    )
                )
            )
        )

        val atTheLimit = mapOf("customer_info" to mapOf("email" to "a".repeat(254)))
        assertTrue(FieldGroupLogic.validate(groups, atTheLimit, language = "fr").isEmpty())

        val overIt = mapOf("customer_info" to mapOf("email" to "a".repeat(255)))
        assertEquals(
            UiText.Raw("Maximum 254 caractères"),
            FieldGroupLogic.validate(groups, overIt, language = "fr").single().message
        )
        assertEquals(
            UiText.Raw("Maximum 254 characters"),
            FieldGroupLogic.validate(groups, overIt, language = "en").single().message
        )
    }

    @Test
    fun `an over-length field with no server message names the SDK's own string`() {
        val groups = listOf(
            FieldGroup(
                key = "customer_info",
                label = null,
                fields = listOf(
                    field(
                        "email",
                        validation = FieldValidation(pattern = null, maxLength = 4, messages = null)
                    ).copy(labels = mapOf("en" to "Email address", "fr" to "Adresse e-mail"))
                )
            )
        )
        val values = mapOf("customer_info" to mapOf("email" to "12345"))

        // The limit is an argument like the label, and a rendered string like the
        // label: a raw Int would be punctuated by the resources' own locale.
        assertEquals(
            UiText.Resource(R.string.paycross_field_too_long, listOf("Adresse e-mail", "4")),
            FieldGroupLogic.validate(groups, values, language = "fr").single().message
        )
    }

    @Test
    fun `an over-length value is reported once, not again for its pattern`() {
        val groups = listOf(
            FieldGroup(
                key = "customer_info",
                label = null,
                fields = listOf(
                    field(
                        "email",
                        validation = FieldValidation(
                            pattern = "^[0-9]+$",
                            maxLength = 4,
                            messages = mapOf("max_length" to "Too long", "pattern" to "Digits only")
                        )
                    )
                )
            )
        )

        val errors = FieldGroupLogic.validate(
            groups,
            mapOf("customer_info" to mapOf("email" to "not digits")),
            language = "en"
        )

        assertEquals(UiText.Raw("Too long"), errors.single().message)
    }
}
