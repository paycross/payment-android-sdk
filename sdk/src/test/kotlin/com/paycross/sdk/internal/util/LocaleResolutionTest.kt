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
    fun `an unsupported override is English, not the session's language`() {
        // The first rung that answers decides. The merchant said German; the SDK
        // has no German, so the shopper gets English. Falling through to the
        // French session here would have the SDK answer a question the merchant
        // had already answered differently.
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(override = "de", session = "fr", device = Locale.US)
        )
    }

    @Test
    fun `an unsupported session locale is English, not the device's language`() {
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(
                override = null,
                session = "de",
                device = Locale.CANADA_FRENCH
            )
        )
    }

    @Test
    fun `a blank tag is not an answer and does not stop the ladder`() {
        // A session minted with an empty locale has said nothing, so the device
        // still gets its turn. The hosted page treats an empty tag the same way,
        // because an empty string is falsy there.
        listOf("", "   ").forEach { blank ->
            assertEquals(
                "blank tag '$blank'",
                Locale.forLanguageTag("fr"),
                LocaleResolution.resolve(
                    override = blank,
                    session = blank,
                    device = Locale.CANADA_FRENCH
                )
            )
        }
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
