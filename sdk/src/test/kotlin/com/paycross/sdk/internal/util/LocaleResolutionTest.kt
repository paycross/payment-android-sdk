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
    fun `an unsupported override falls through to the session`() {
        // Each candidate is matched on its own, exactly as the hosted page's
        // resolveLanguage does, so a merchant asking for a language the SDK does
        // not ship still leaves the session's own locale in play.
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = "de", session = "fr", device = Locale.US)
        )
    }

    @Test
    fun `an override and a device the SDK cannot speak land on English`() {
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(override = "de", session = null, device = Locale.GERMANY)
        )
    }

    @Test
    fun `an override with a region falls back to its primary subtag`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = "fr-CA", session = null, device = Locale.US)
        )
    }

    @Test
    fun `a blank or malformed override still gives the session and the device their turn`() {
        listOf(null, "", "   ", "!!", "fr_CA").forEach { override ->
            assertEquals(
                "override '$override' with a French session",
                Locale.forLanguageTag("fr"),
                LocaleResolution.resolve(override, session = "fr", device = Locale.US)
            )
            assertEquals(
                "override '$override' with a French device",
                Locale.forLanguageTag("fr"),
                LocaleResolution.resolve(override, session = null, device = Locale.CANADA_FRENCH)
            )
        }
    }

    @Test
    fun `an unsupported session locale falls through to the device`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(
                override = null,
                session = "de",
                device = Locale.CANADA_FRENCH
            )
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

    // --- The amount's locale, which is not clamped to the shipped languages ---

    @Test
    fun `a device the SDK cannot speak still formats the amount its own way`() {
        // The words go English because there are no German ones; the number does
        // not have to follow them, because every locale can format a number.
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(override = null, session = null, device = Locale.GERMANY)
        )
        assertEquals(
            Locale.GERMANY,
            LocaleResolution.formattingLocale(
                override = null,
                session = null,
                device = Locale.GERMANY
            )
        )
    }

    @Test
    fun `the amount keeps the region the strings drop`() {
        // fr-CH words are France's French, because that is all the SDK ships,
        // but Swiss grouping is not France's and the amount keeps it.
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = null, session = "fr-CH", device = Locale.US)
        )
        assertEquals(
            Locale.forLanguageTag("fr-CH"),
            LocaleResolution.formattingLocale(
                override = null,
                session = "fr-CH",
                device = Locale.US
            )
        )
    }

    @Test
    fun `the amount follows the override first, then the session, then the device`() {
        assertEquals(
            Locale.forLanguageTag("de-AT"),
            LocaleResolution.formattingLocale("de-AT", "fr-CA", Locale.US)
        )
        assertEquals(
            Locale.forLanguageTag("fr-CA"),
            LocaleResolution.formattingLocale(null, "fr-CA", Locale.US)
        )
        assertEquals(Locale.US, LocaleResolution.formattingLocale(null, null, Locale.US))
    }

    @Test
    fun `an unreadable tag does not get to format the amount`() {
        listOf("", "   ", "!!", "fr_CA").forEach { junk ->
            assertEquals(
                "junk tag '$junk'",
                Locale.US,
                LocaleResolution.formattingLocale(junk, junk, Locale.US)
            )
        }
    }

    @Test
    fun `a misshapen override does not get to format the amount`() {
        // A typo in init(locale = …) should cost nothing. forLanguageTag is
        // forgiving — it keeps whatever well-formed prefix it finds and drops the
        // rest — so without a shape check ahead of it, a typo becomes a real
        // locale nobody meant and punctuates the amount for somewhere the shopper
        // has never been. The device supplies the formatting instead.
        listOf("fr_CA", "f", "frrrr", "1234", "fr-", "en--US", "fr CA", "-fr").forEach { typo ->
            assertEquals(
                "typo '$typo'",
                Locale.GERMANY,
                LocaleResolution.formattingLocale(typo, session = null, device = Locale.GERMANY)
            )
        }
    }

    @Test
    fun `a typo naming no shipped language leaves the words English`() {
        listOf("fr_CA", "f", "frrrr", "1234", "fr CA", "-fr").forEach { typo ->
            assertEquals(
                "typo '$typo'",
                Locale.forLanguageTag("en"),
                LocaleResolution.resolve(typo, session = null, device = Locale.GERMANY)
            )
        }
    }

    @Test
    fun `the words are read more forgivingly than the amount is`() {
        // "fr-" is misshapen, so it does not format the amount, but it still
        // draws French words: forLanguageTag reads its well-formed prefix, and
        // the strings ladder only has to decide between the two languages the
        // SDK ships. Being eager about the words is harmless; being eager about
        // a number's punctuation is not.
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve("fr-", session = null, device = Locale.GERMANY)
        )
        assertEquals(
            Locale.GERMANY,
            LocaleResolution.formattingLocale("fr-", session = null, device = Locale.GERMANY)
        )
    }

    @Test
    fun `a misshapen session locale leaves the amount to the device too`() {
        assertEquals(
            Locale.GERMANY,
            LocaleResolution.formattingLocale(
                override = null,
                session = "fr_CA",
                device = Locale.GERMANY
            )
        )
    }

    @Test
    fun `a well-shaped tag still formats the amount, script and region and all`() {
        listOf("fr-CA", "zh-Hans-CN", "de-AT", "pt-BR", "en-GB").forEach { tag ->
            assertEquals(
                "well-shaped tag '$tag'",
                Locale.forLanguageTag(tag),
                LocaleResolution.formattingLocale(tag, session = null, device = Locale.US)
            )
        }
    }

    @Test
    fun `the amount falls back to the platform default when nothing names a locale`() {
        assertEquals(
            Locale.getDefault(),
            LocaleResolution.formattingLocale(null, null, null)
        )
    }

    @Test
    fun `the shipped set is exactly the resource folders that exist`() {
        // values/ and values-fr/. A language added here without its folder ships
        // a locale that resolves to nothing and paints English anyway.
        assertEquals(listOf("en", "fr"), LocaleResolution.SUPPORTED_TAGS)
    }
}
