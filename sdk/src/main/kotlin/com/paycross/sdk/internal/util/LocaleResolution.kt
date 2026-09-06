package com.paycross.sdk.internal.util

import java.util.Locale

/**
 * Picks the language the payment sheet draws in, and the locale it formats the
 * amount with. They are not always the same one.
 *
 * [resolve] answers with a language the SDK actually ships strings for. The
 * merchant's override, the session's `locale` and the device are each matched on
 * their own — the whole tag first, then its primary subtag, so `fr-CA` reaches
 * the French strings — and one that matches nothing falls through to the next
 * rather than ending the ladder. That is the hosted checkout page's
 * `matchSupportedLocale` / `resolveLanguage` rule exactly, so a shopper who
 * bounces between the page and a native sheet reads one language.
 *
 * [formattingLocale] answers with the first locale anyone actually named,
 * whether or not the SDK has words for it. Number formatting works for every
 * locale on the platform, so clamping it to the shipped set would take a German
 * shopper's `12,34 €` away to no purpose.
 *
 * Nothing here throws. A tag that cannot be parsed is not an answer.
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
        val parsed = parse(tag) ?: return null
        val exact = parsed.toLanguageTag()
        val supported = SUPPORTED_TAGS.firstOrNull { it.equals(exact, ignoreCase = true) }
            ?: SUPPORTED_TAGS.firstOrNull { it.equals(parsed.language, ignoreCase = true) }
            ?: return null
        return Locale.forLanguageTag(supported)
    }

    /**
     * The language the sheet's strings are drawn in.
     *
     * @param override The merchant's `PayCross.init(locale = …)`.
     * @param session The session payload's `locale`.
     * @param device The device's own locale, or null when there is none to read.
     */
    fun resolve(override: String?, session: String?, device: Locale?): Locale =
        match(override) ?: match(session) ?: match(device?.toLanguageTag()) ?: DEFAULT

    /**
     * The locale the amount is formatted with: the first of [override], [session]
     * and [device] that names a language at all, kept whole and **not** narrowed
     * to the shipped set.
     *
     * So a German handset reads English words over a German-formatted amount,
     * and a `fr-CH` session gets Swiss-French grouping under French words. The
     * region is what carries the formatting, and dropping it to reach `fr` would
     * quietly move a Swiss shopper onto France's conventions.
     */
    fun formattingLocale(override: String?, session: String?, device: Locale?): Locale =
        parse(override) ?: parse(session) ?: device ?: Locale.getDefault()

    /**
     * [tag] as a locale, or null when it names no language. `forLanguageTag`
     * drops everything from the first ill-formed subtag on, so a tag it cannot
     * read at all leaves the language empty rather than raising anything.
     */
    private fun parse(tag: String?): Locale? {
        val candidate = tag?.trim().orEmpty().ifEmpty { return null }
        return Locale.forLanguageTag(candidate).takeIf { it.language.isNotEmpty() }
    }
}
