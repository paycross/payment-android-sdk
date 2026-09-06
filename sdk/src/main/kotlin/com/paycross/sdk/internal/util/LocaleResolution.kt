package com.paycross.sdk.internal.util

import java.util.Locale

/**
 * Picks the language the payment sheet draws in.
 *
 * The ladder is the merchant's override, then the session's `locale`, then the
 * device, then English. Each candidate is matched on its own against the
 * languages the SDK actually ships — the whole tag first, then its primary
 * subtag, so `fr-CA` reaches the French strings — and a candidate that matches
 * nothing falls through to the next rather than ending the ladder. That is the
 * hosted checkout page's `matchSupportedLocale` / `resolveLanguage` rule, so a
 * shopper who bounces between the page and a native sheet reads one language.
 *
 * Nothing here throws. A tag the SDK cannot parse is a tag it does not ship, and
 * both answers are the same one: fall through.
 */
internal object LocaleResolution {

    /**
     * The languages `sdk/src/main/res/values*` actually holds strings for, first
     * one last-resort. A tag added here without its `values-<tag>` folder
     * resolves to a locale whose strings do not exist, and the sheet paints the
     * default `values/` English anyway — silently, since resource lookup has no
     * failure to report.
     */
    val SUPPORTED_TAGS = listOf("en", "fr")

    /** What the sheet falls back to when no candidate matches. */
    val DEFAULT: Locale = Locale.forLanguageTag(SUPPORTED_TAGS.first())

    /**
     * The shipped language [tag] asks for, or null when the SDK ships none —
     * which is also the answer for a null, blank or malformed tag.
     */
    fun match(tag: String?): Locale? {
        val candidate = tag?.trim().orEmpty().ifEmpty { return null }
        val parsed = Locale.forLanguageTag(candidate)
        // forLanguageTag drops everything from the first ill-formed subtag on,
        // so a tag it cannot read at all leaves the language empty rather than
        // raising anything.
        if (parsed.language.isEmpty()) return null

        val exact = parsed.toLanguageTag()
        val supported = SUPPORTED_TAGS.firstOrNull { it.equals(exact, ignoreCase = true) }
            ?: SUPPORTED_TAGS.firstOrNull { it.equals(parsed.language, ignoreCase = true) }
            ?: return null
        return Locale.forLanguageTag(supported)
    }

    /**
     * @param override The merchant's `PayCross.init(locale = …)`.
     * @param session The session payload's `locale`.
     * @param device The device's own locale, or null when there is none to read.
     */
    fun resolve(override: String?, session: String?, device: Locale?): Locale =
        match(override) ?: match(session) ?: match(device?.toLanguageTag()) ?: DEFAULT
}
