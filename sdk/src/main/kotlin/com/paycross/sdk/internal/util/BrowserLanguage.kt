package com.paycross.sdk.internal.util

internal object BrowserLanguage {

    // Backend validates browser_info.language with max:10; EMVCo 3DS caps
    // browserLanguage at 8, so trimming to whole subtags is spec-compliant.
    private const val MAX_LENGTH = 10

    /**
     * Clamps a BCP-47 tag to the backend's 10-character limit: everything
     * from the first single-character subtag (extension/private-use
     * singletons like "u"/"x") is dropped, then whole leading subtags are
     * kept while they fit. Never returns empty for a non-empty input.
     */
    fun clamp(tag: String): String {
        val subtags = tag.split("-")
        val kept = subtags.takeWhile { it.length != 1 }.ifEmpty { subtags.take(1) }
        var result = kept.first().take(MAX_LENGTH)
        for (subtag in kept.drop(1)) {
            val candidate = "$result-$subtag"
            if (candidate.length > MAX_LENGTH) break
            result = candidate
        }
        return result
    }
}
