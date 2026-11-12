package com.paycross.sdk.internal.util

internal object BrowserLanguage {
    // WHY: the backend allows 10 characters and EMVCo 3DS caps browserLanguage at 8, so whole subtags are kept.
    private const val MAX_LENGTH = 10

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
