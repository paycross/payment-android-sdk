package com.paycross.sdk.internal.util

import java.util.Locale

internal data class SheetLocales(val strings: Locale, val amount: Locale)

internal object LocaleResolution {

    val SUPPORTED_TAGS = listOf("en", "fr")

    val DEFAULT: Locale = Locale.forLanguageTag(SUPPORTED_TAGS.first())

    fun match(tag: String?): Locale? {
        val parsed = parse(tag) ?: return null
        val exact = parsed.toLanguageTag()
        val supported = SUPPORTED_TAGS.firstOrNull { it.equals(exact, ignoreCase = true) }
            ?: SUPPORTED_TAGS.firstOrNull { it.equals(parsed.language, ignoreCase = true) }
            ?: return null
        return Locale.forLanguageTag(supported)
    }

    fun resolve(override: String?, session: String?, device: List<Locale>): Locale =
        match(override)
            ?: match(session)
            ?: device.firstNotNullOfOrNull { match(it.toLanguageTag()) }
            ?: DEFAULT

    fun formattingLocale(override: String?, session: String?, device: List<Locale>): Locale =
        wellShaped(override)
            ?: wellShaped(session)
            ?: device.firstOrNull()
            ?: Locale.getDefault(Locale.Category.FORMAT)

    fun sheetLocales(override: String?, session: String?, device: List<Locale>): SheetLocales =
        SheetLocales(
            strings = resolve(override, session, device),
            amount = formattingLocale(override, session, device)
        )

    // WHY: Locale.forLanguageTag keeps only the well-formed prefix of a tag and drops the rest silently, so the shape is checked first.
    private val WELL_SHAPED = Regex("^[A-Za-z]{2,3}(-[A-Za-z0-9]+)*$")

    private fun wellShaped(tag: String?): Locale? {
        val candidate = normalised(tag) ?: return null
        if (!WELL_SHAPED.matches(candidate)) return null
        return Locale.forLanguageTag(candidate).takeIf { it.language.isNotEmpty() }
    }

    private fun parse(tag: String?): Locale? {
        val candidate = normalised(tag) ?: return null
        return Locale.forLanguageTag(candidate).takeIf { it.language.isNotEmpty() }
    }

    // WHY: Locale.toString() returns fr_FR, which BCP 47 does not accept and forLanguageTag reads as no language.
    private fun normalised(tag: String?): String? =
        tag?.trim()?.replace('_', '-')?.ifEmpty { null }
}
