package com.paycross.sdk.internal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class LocaleResolutionTest {

    @Test
    fun `the merchant override wins over the session and the device`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = "fr", session = "en", device = listOf(Locale.US))
        )
    }

    @Test
    fun `the session locale wins over the device`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = null, session = "fr", device = listOf(Locale.US))
        )
    }

    @Test
    fun `a session tag with a region falls back to its primary subtag`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = null, session = "fr-CA", device = listOf(Locale.US))
        )
    }

    @Test
    fun `a device the SDK has no strings for gets English`() {
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(override = null, session = null, device = listOf(Locale.GERMANY))
        )
    }

    @Test
    fun `a malformed tag gets English rather than throwing`() {
        listOf("", "   ", "!!", "français", "zz-ZZ-", "12345").forEach { tag ->
            assertEquals(
                "malformed tag $tag",
                Locale.forLanguageTag("en"),
                LocaleResolution.resolve(override = tag, session = tag, device = emptyList())
            )
        }
    }

    @Test
    fun `an unsupported override falls through to the session`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = "de", session = "fr", device = listOf(Locale.US))
        )
    }

    @Test
    fun `an override and a device the SDK cannot speak land on English`() {
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(override = "de", session = null, device = listOf(Locale.GERMANY))
        )
    }

    @Test
    fun `an override with a region falls back to its primary subtag`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = "fr-CA", session = null, device = listOf(Locale.US))
        )
    }

    @Test
    fun `a blank or malformed override still gives the session and the device their turn`() {
        listOf(null, "", "   ", "!!", "français").forEach { override ->
            assertEquals(
                "override '$override' with a French session",
                Locale.forLanguageTag("fr"),
                LocaleResolution.resolve(override, session = "fr", device = listOf(Locale.US))
            )
            assertEquals(
                "override '$override' with a French device",
                Locale.forLanguageTag("fr"),
                LocaleResolution.resolve(override, session = null, device = listOf(Locale.CANADA_FRENCH))
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
                device = listOf(Locale.CANADA_FRENCH)
            )
        )
    }

    @Test
    fun `nothing anywhere is English`() {
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(override = null, session = null, device = emptyList())
        )
    }

    @Test
    fun `a French device with no override and no session gets French`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = null, session = null, device = listOf(Locale.CANADA_FRENCH))
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
    fun `a device the SDK cannot speak still formats the amount its own way`() {
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(override = null, session = null, device = listOf(Locale.GERMANY))
        )
        assertEquals(
            Locale.GERMANY,
            LocaleResolution.formattingLocale(
                override = null,
                session = null,
                device = listOf(Locale.GERMANY)
            )
        )
    }

    @Test
    fun `the amount keeps the region the strings drop`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = null, session = "fr-CH", device = listOf(Locale.US))
        )
        assertEquals(
            Locale.forLanguageTag("fr-CH"),
            LocaleResolution.formattingLocale(
                override = null,
                session = "fr-CH",
                device = listOf(Locale.US)
            )
        )
    }

    @Test
    fun `the amount follows the override first, then the session, then the device`() {
        assertEquals(
            Locale.forLanguageTag("de-AT"),
            LocaleResolution.formattingLocale("de-AT", "fr-CA", listOf(Locale.US))
        )
        assertEquals(
            Locale.forLanguageTag("fr-CA"),
            LocaleResolution.formattingLocale(null, "fr-CA", listOf(Locale.US))
        )
        assertEquals(Locale.US, LocaleResolution.formattingLocale(null, null, listOf(Locale.US)))
    }

    @Test
    fun `an unreadable tag does not get to format the amount`() {
        listOf("", "   ", "!!", "français").forEach { junk ->
            assertEquals(
                "junk tag '$junk'",
                Locale.US,
                LocaleResolution.formattingLocale(junk, junk, listOf(Locale.US))
            )
        }
    }

    @Test
    fun `a misshapen override does not get to format the amount`() {
        listOf("f", "français", "frrrr", "1234", "fr-", "en--US", "fr CA", "-fr").forEach { typo ->
            assertEquals(
                "typo '$typo'",
                Locale.GERMANY,
                LocaleResolution.formattingLocale(typo, session = null, device = listOf(Locale.GERMANY))
            )
        }
    }

    @Test
    fun `a typo naming no shipped language leaves the words English`() {
        listOf("f", "français", "frrrr", "1234", "fr CA", "-fr").forEach { typo ->
            assertEquals(
                "typo '$typo'",
                Locale.forLanguageTag("en"),
                LocaleResolution.resolve(typo, session = null, device = listOf(Locale.GERMANY))
            )
        }
    }

    @Test
    fun `the words are read more forgivingly than the amount is`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve("fr-", session = null, device = listOf(Locale.GERMANY))
        )
        assertEquals(
            Locale.GERMANY,
            LocaleResolution.formattingLocale("fr-", session = null, device = listOf(Locale.GERMANY))
        )
    }

    @Test
    fun `a misshapen session locale leaves the amount to the device too`() {
        assertEquals(
            Locale.GERMANY,
            LocaleResolution.formattingLocale(
                override = null,
                session = "français",
                device = listOf(Locale.GERMANY)
            )
        )
    }

    @Test
    fun `a merchant typo picks neither the words nor the amount`() {
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve("français", session = null, device = listOf(Locale.GERMANY))
        )
        assertEquals(
            Locale.GERMANY,
            LocaleResolution.formattingLocale("français", session = null, device = listOf(Locale.GERMANY))
        )
    }

    @Test
    fun `an underscore reads as a hyphen, for both the words and the amount`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve("fr_FR", session = null, device = listOf(Locale.US))
        )
        assertEquals(
            Locale.forLanguageTag("fr-FR"),
            LocaleResolution.formattingLocale("fr_FR", session = null, device = listOf(Locale.US))
        )
        assertEquals(Locale.forLanguageTag("fr"), LocaleResolution.match("fr_CA"))
        assertEquals(
            Locale.forLanguageTag("de-DE"),
            LocaleResolution.formattingLocale("de_DE", session = null, device = listOf(Locale.US))
        )
    }

    @Test
    fun `a well-shaped tag still formats the amount, script and region and all`() {
        listOf("fr-CA", "zh-Hans-CN", "de-AT", "pt-BR", "en-GB").forEach { tag ->
            assertEquals(
                "well-shaped tag '$tag'",
                Locale.forLanguageTag(tag),
                LocaleResolution.formattingLocale(tag, session = null, device = listOf(Locale.US))
            )
        }
    }

    @Test
    fun `the amount falls back to the platform default when nothing names a locale`() {
        assertEquals(
            Locale.getDefault(Locale.Category.FORMAT),
            LocaleResolution.formattingLocale(null, null, emptyList())
        )
    }

    @Test
    fun `the device rung keeps its region, which changes how money is written`() {
        val swiss = Locale.forLanguageTag("de-CH")
        val resolved = LocaleResolution.formattingLocale(null, null, listOf(swiss))

        assertEquals(swiss, resolved)
        assertEquals("CH", resolved.country)
        assertNotEquals(
            Amounts.formatMinor(123450, "EUR", Locale.forLanguageTag("de")),
            Amounts.formatMinor(123450, "EUR", resolved)
        )
    }

    @Test
    fun `the device rung is used whole even when the words fall back to English`() {
        val austrian = Locale.forLanguageTag("de-AT")
        val locales = LocaleResolution.sheetLocales(null, null, listOf(austrian))

        assertEquals(Locale.forLanguageTag("en"), locales.strings)
        assertEquals(austrian, locales.amount)
        assertEquals("AT", locales.amount.country)
    }

    @Test
    fun `every language in the shipped set resolves to itself`() {
        LocaleResolution.SUPPORTED_TAGS.forEach { tag ->
            assertEquals(
                "shipped tag '$tag'",
                Locale.forLanguageTag(tag),
                LocaleResolution.resolve(tag, session = null, device = emptyList())
            )
        }
    }

    @Test
    fun `a second device preference is reached when the first is not shipped`() {
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(
                override = null,
                session = null,
                device = listOf(Locale.forLanguageTag("de-DE"), Locale.forLanguageTag("fr-FR"))
            )
        )
    }

    @Test
    fun `the earliest shipped device preference wins`() {
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(
                override = null,
                session = null,
                device = listOf(
                    Locale.forLanguageTag("de-DE"),
                    Locale.forLanguageTag("en-GB"),
                    Locale.forLanguageTag("fr-FR")
                )
            )
        )
    }

    @Test
    fun `a device list with nothing shipped is English`() {
        assertEquals(
            Locale.forLanguageTag("en"),
            LocaleResolution.resolve(
                override = null,
                session = null,
                device = listOf(Locale.forLanguageTag("de-DE"), Locale.forLanguageTag("es-ES"))
            )
        )
    }

    @Test
    fun `the amount follows the handset's first choice, shipped or not`() {
        val device = listOf(Locale.forLanguageTag("de-DE"), Locale.forLanguageTag("fr-FR"))
        assertEquals(
            Locale.forLanguageTag("fr"),
            LocaleResolution.resolve(override = null, session = null, device = device)
        )
        assertEquals(
            Locale.forLanguageTag("de-DE"),
            LocaleResolution.formattingLocale(override = null, session = null, device = device)
        )
    }

    @Test
    fun `sheetLocales answers the words and the amount from one set of candidates`() {
        val locales = LocaleResolution.sheetLocales(
            override = null,
            session = "fr-CH",
            device = listOf(Locale.GERMANY)
        )

        assertEquals(Locale.forLanguageTag("fr"), locales.strings)
        assertEquals(Locale.forLanguageTag("fr-CH"), locales.amount)
    }

    @Test
    fun `sheetLocales on a handset the SDK cannot speak splits the two answers`() {
        val locales = LocaleResolution.sheetLocales(
            override = null,
            session = null,
            device = listOf(Locale.GERMANY)
        )

        assertEquals(Locale.forLanguageTag("en"), locales.strings)
        assertEquals(Locale.GERMANY, locales.amount)
    }

}
