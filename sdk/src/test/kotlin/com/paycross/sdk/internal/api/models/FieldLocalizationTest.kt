package com.paycross.sdk.internal.api.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one ladder every merchant-supplied string in `field_groups` is read
 * through: the resolved language's translation, else the singular key the
 * session was minted with, else the wire name.
 *
 * The middle rung is the one worth pinning. A language the backend has no
 * translation for and a session minted before the maps existed are the same case
 * here, and both have to keep the string the session did carry rather than fall
 * all the way to a field named `vat_id` on screen.
 */
class FieldLocalizationTest {

    private val group = FieldGroup(
        key = "customer_info",
        label = "Your details",
        labels = mapOf("en" to "Your details", "fr" to "Vos coordonnées"),
        fields = null
    )

    private val email = FieldDefinition(
        name = "email",
        type = "email",
        label = "Email address",
        labels = mapOf("en" to "Email address", "fr" to "Adresse e-mail"),
        placeholder = "email@example.com",
        placeholders = mapOf("en" to "email@example.com", "fr" to "courriel@exemple.com"),
        required = true,
        readonly = false,
        value = null,
        condition = null,
        options = null,
        validation = FieldValidation(
            pattern = null,
            maxLength = 254,
            messages = mapOf("required" to "This field is required"),
            messagesI18n = mapOf(
                "en" to mapOf("required" to "This field is required"),
                "fr" to mapOf("required" to "Ce champ est obligatoire")
            )
        )
    )

    @Test
    fun `every string is read in the resolved language`() {
        assertEquals("Vos coordonnées", group.localizedLabel("fr"))
        assertEquals("Adresse e-mail", email.localizedLabel("fr"))
        assertEquals("courriel@exemple.com", email.localizedPlaceholder("fr"))
        assertEquals(
            "Ce champ est obligatoire",
            email.validation!!.localizedMessage("fr", "required")
        )
    }

    @Test
    fun `a language the backend has no translation for keeps the session's own string`() {
        assertEquals("Your details", group.localizedLabel("de"))
        assertEquals("Email address", email.localizedLabel("de"))
        assertEquals("email@example.com", email.localizedPlaceholder("de"))
        assertEquals(
            "This field is required",
            email.validation!!.localizedMessage("de", "required")
        )
    }

    @Test
    fun `a session minted before the maps existed reads its singular keys`() {
        val old = email.copy(
            labels = null,
            placeholders = null,
            validation = FieldValidation(
                pattern = null,
                maxLength = 254,
                messages = mapOf("required" to "This field is required")
            )
        )

        assertEquals("Your details", group.copy(labels = null).localizedLabel("fr"))
        assertEquals("Email address", old.localizedLabel("fr"))
        assertEquals("email@example.com", old.localizedPlaceholder("fr"))
        assertEquals("This field is required", old.validation!!.localizedMessage("fr", "required"))
    }

    @Test
    fun `a field with nothing to show is named after its wire key`() {
        val bare = email.copy(label = null, labels = null, placeholder = null, placeholders = null)

        assertEquals("email", bare.localizedLabel("fr"))
        assertNull(bare.localizedPlaceholder("fr"))
        assertNull(group.copy(label = null, labels = null).localizedLabel("fr"))
    }

    @Test
    fun `an option falls back to the value that gets submitted`() {
        val translated = FieldOption(
            value = "mrs",
            label = "Mrs",
            labels = mapOf("en" to "Mrs", "fr" to "Madame")
        )

        assertEquals("Madame", translated.localizedLabel("fr"))
        assertEquals("Mrs", translated.localizedLabel("de"))
        assertEquals("mrs", FieldOption(value = "mrs", label = null).localizedLabel("fr"))
    }

    @Test
    fun `a rule the merchant wrote no message for stays unanswered`() {
        // The SDK's own translated fallback covers it; a null here is what asks
        // for that, and a blank string would silently draw an empty error.
        assertNull(email.validation!!.localizedMessage("fr", "pattern"))
    }

    @Test
    fun `a rule missing from the resolved language falls back on its own`() {
        val partial = email.validation!!.copy(
            messages = mapOf("required" to "This field is required", "pattern" to "Wrong format"),
            messagesI18n = mapOf("fr" to mapOf("required" to "Ce champ est obligatoire"))
        )

        assertEquals("Ce champ est obligatoire", partial.localizedMessage("fr", "required"))
        // French is present but carries no `pattern`, so the singular map answers
        // that one rule. Falling back a whole language at a time would take the
        // French `required` sentence away with it.
        assertEquals("Wrong format", partial.localizedMessage("fr", "pattern"))
    }
}
