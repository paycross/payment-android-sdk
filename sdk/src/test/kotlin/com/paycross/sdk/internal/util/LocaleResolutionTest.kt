package com.paycross.sdk.internal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class LocaleResolutionTest {

    @Test
    fun `the merchant override wins over the session and the device`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = "fr", session = "en", device = Locale.US)
        )
    }

    @Test
    fun `the session locale wins over the device`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = null, session = "fr", device = Locale.US)
        )
    }

    @Test
    fun `a session tag with a region falls back to its primary subtag`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = null, session = "fr-CA", device = Locale.US)
        )
    }

    @Test
    fun `a device the SDK has no strings for gets English`() {
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(override = null, session = null, device = Locale.GERMANY)
        )
    }

    @Test
    fun `a malformed tag gets English rather than throwing`() {
        listOf("", "   ", "!!", "fr_CA", "zz-ZZ-", "12345").forEach { tag ->
            assertEquals(
                "malformed tag $tag",
                Locale.forLanguageTag("en"),
                LocaleResolution.resolve(override = tag, session = tag, device = null)
            )
        }
    }

    @Test
    fun `an unsupported override falls through to the session rather than to English`() {
        // The hosted page runs each candidate through the matcher on its own, so
        // a merchant asking for a language the SDK does not ship still leaves the
        // session's own locale in play.
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = "de", session = "fr", device = Locale.US)
        )
    }

    @Test
    fun `nothing anywhere is English`() {
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(override = null, session = null, device = null)
        )
    }

    @Test
    fun `a French device with no override and no session gets French`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = null, session = null, device = Locale.CANADA_FRENCH)
        )
    }

    @Test
    fun `match returns null for a language the SDK does not ship`() {
        assertNull(LocaleResolution.match("de"))
        assertNull(LocaleResolution.match("de-DE"))
        assertNull(LocaleResolution.match(null))
        assertNull(LocaleResolution.match(""))
        assertNull(LocaleResolution.match("!!"))
    }

    @Test
    fun `match is case-insensitive and tolerates surrounding whitespace`() {
        assertEquals(Locale.forLanguageTag("fr"), LocaleResolution.match(" FR-ca "))
        assertEquals(Locale.forLanguageTag("en"), LocaleResolution.match("EN"))
    }

    @Test
    fun `the shipped set is exactly the resource folders that exist`() {
        // values/ and values-fr/. A language added here without its folder ships
        // a locale that resolves to nothing and paints English anyway.
        assertEquals(listOf("en", "fr"), LocaleResolution.SUPPORTED_TAGS)
    }
}
