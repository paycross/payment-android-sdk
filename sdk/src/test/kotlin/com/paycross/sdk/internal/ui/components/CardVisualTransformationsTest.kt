package com.paycross.sdk.internal.ui.components

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The caret lives in the mapping, so it is checked at every offset rather than
 * at the few a hand-written example would reach.
 */
class CardVisualTransformationsTest {

    @Test
    fun `card number renders as groups of four`() {
        assertEquals("", groupCardNumber(""))
        assertEquals("1", groupCardNumber("1"))
        assertEquals("1234", groupCardNumber("1234"))
        assertEquals("1234 5", groupCardNumber("12345"))
        assertEquals("4111 1111 1117 0000", groupCardNumber("4111111111170000"))
        assertEquals("6011 1111 1111 1111 117", groupCardNumber("6011111111111111117"))
    }

    @Test
    fun `expiry renders as MM slash YY`() {
        assertEquals("", formatExpiry(""))
        assertEquals("1", formatExpiry("1"))
        assertEquals("12", formatExpiry("12"))
        assertEquals("12/2", formatExpiry("122"))
        assertEquals("12/28", formatExpiry("1228"))
    }

    @Test
    fun `card number transformation renders the grouped text`() {
        val transformed = CardNumberVisualTransformation.filter(AnnotatedString("4111111111170000"))
        assertEquals("4111 1111 1117 0000", transformed.text.text)
    }

    @Test
    fun `expiry transformation renders the slashed text`() {
        val transformed = ExpiryVisualTransformation.filter(AnnotatedString("1228"))
        assertEquals("12/28", transformed.text.text)
    }

    @Test
    fun `card number offsets round-trip for every digit count and every caret position`() {
        for (digitCount in 0..MAX_PAN_DIGITS) {
            val rendered = groupCardNumber(DIGITS.take(digitCount))
            val mapping = CardNumberOffsetMapping(digitCount)

            for (offset in 0..digitCount) {
                val transformed = mapping.originalToTransformed(offset)
                assertTrue(
                    "digits=$digitCount offset=$offset -> $transformed is outside '$rendered'",
                    transformed in 0..rendered.length
                )
                assertEquals(
                    "digits=$digitCount offset=$offset does not round-trip",
                    offset,
                    mapping.transformedToOriginal(transformed)
                )
            }
        }
    }

    @Test
    fun `card number transformed offsets always land on a digit index`() {
        for (digitCount in 0..MAX_PAN_DIGITS) {
            val rendered = groupCardNumber(DIGITS.take(digitCount))
            val mapping = CardNumberOffsetMapping(digitCount)

            for (offset in 0..rendered.length) {
                val original = mapping.transformedToOriginal(offset)
                assertTrue(
                    "digits=$digitCount rendered offset=$offset -> $original is out of range",
                    original in 0..digitCount
                )
            }
        }
    }

    @Test
    fun `card number offsets for a 16 digit pan`() {
        val mapping = CardNumberOffsetMapping(16)
        // "1234 5678 9012 3456" - one extra step at each of the three spaces.
        val expected = listOf(0, 1, 2, 3, 4, 6, 7, 8, 9, 11, 12, 13, 14, 16, 17, 18, 19)
        assertEquals(expected, (0..16).map { mapping.originalToTransformed(it) })

        // Both sides of a space collapse onto the digit index before it.
        val back = listOf(0, 1, 2, 3, 4, 4, 5, 6, 7, 8, 8, 9, 10, 11, 12, 12, 13, 14, 15, 16)
        assertEquals(back, (0..19).map { mapping.transformedToOriginal(it) })
    }

    @Test
    fun `expiry offsets round-trip for every digit count and every caret position`() {
        for (digitCount in 0..EXPIRY_DIGITS) {
            val rendered = formatExpiry(DIGITS.take(digitCount))
            val mapping = ExpiryOffsetMapping(digitCount)

            for (offset in 0..digitCount) {
                val transformed = mapping.originalToTransformed(offset)
                assertTrue(
                    "digits=$digitCount offset=$offset -> $transformed is outside '$rendered'",
                    transformed in 0..rendered.length
                )
                assertEquals(
                    "digits=$digitCount offset=$offset does not round-trip",
                    offset,
                    mapping.transformedToOriginal(transformed)
                )
            }
            for (offset in 0..rendered.length) {
                assertTrue(mapping.transformedToOriginal(offset) in 0..digitCount)
            }
        }
    }

    @Test
    fun `expiry offsets for a full date`() {
        val mapping = ExpiryOffsetMapping(4)
        assertEquals(listOf(0, 1, 2, 4, 5), (0..4).map { mapping.originalToTransformed(it) })
        assertEquals(listOf(0, 1, 2, 2, 3, 4), (0..5).map { mapping.transformedToOriginal(it) })
    }

    @Test
    fun `offsets outside the text are clamped rather than throwing`() {
        // Compose queries the previous frame's mapping while the digit count is
        // changing, and it treats an out-of-range result as a crash.
        val card = CardNumberOffsetMapping(16)
        assertEquals(0, card.originalToTransformed(-5))
        assertEquals(19, card.originalToTransformed(99))
        assertEquals(0, card.transformedToOriginal(-5))
        assertEquals(16, card.transformedToOriginal(99))

        val expiry = ExpiryOffsetMapping(4)
        assertEquals(0, expiry.originalToTransformed(-5))
        assertEquals(5, expiry.originalToTransformed(99))
        assertEquals(0, expiry.transformedToOriginal(-5))
        assertEquals(4, expiry.transformedToOriginal(99))
    }

    private companion object {
        const val MAX_PAN_DIGITS = 19
        const val EXPIRY_DIGITS = 4
        const val DIGITS = "1234567890123456789"
    }
}
