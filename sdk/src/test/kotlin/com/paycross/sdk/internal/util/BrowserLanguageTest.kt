package com.paycross.sdk.internal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserLanguageTest {

    @Test
    fun `drops extension subtags from the first singleton onward`() {
        assertEquals("en-US", BrowserLanguage.clamp("en-US-u-rg-lvzzzz"))
    }

    @Test
    fun `passes short tags through unchanged`() {
        assertEquals("en-US", BrowserLanguage.clamp("en-US"))
        assertEquals("en", BrowserLanguage.clamp("en"))
    }

    @Test
    fun `keeps a tag that is exactly ten characters`() {
        assertEquals("zh-Hant-TW", BrowserLanguage.clamp("zh-Hant-TW"))
        assertEquals("zh-Hant-TW", BrowserLanguage.clamp("zh-Hant-TW-u-co-pinyin"))
    }

    @Test
    fun `trims whole subtags when the tag exceeds ten characters`() {
        assertEquals("en-US", BrowserLanguage.clamp("en-US-POSIX"))
    }

    @Test
    fun `hard-truncates a pathological first subtag to ten characters`() {
        assertEquals("abcdefghij", BrowserLanguage.clamp("abcdefghijklmnop"))
    }

    @Test
    fun `never returns empty for non-empty input`() {
        assertEquals("x", BrowserLanguage.clamp("x-private"))
    }
}
