package com.roipaste.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipValidatorTest {
    @Test
    fun emptyAndNullAreEmpty() {
        assertEquals(ClipValidation.Empty, ClipValidator.validate(null))
        assertEquals(ClipValidation.Empty, ClipValidator.validate(""))
    }

    @Test
    fun acceptsVietnameseAndEmoji() {
        val text = "Xin chào từ ROIPaste 👋"
        val result = ClipValidator.validate(text)
        assertTrue(result is ClipValidation.Valid)
        assertEquals(text, (result as ClipValidation.Valid).text)
    }

    @Test
    fun acceptsExactByteLimit() {
        val text = "a".repeat(ClipLimits.MAX_TEXT_BYTES)
        val result = ClipValidator.validate(text)
        assertTrue(result is ClipValidation.Valid)
        assertEquals(ClipLimits.MAX_TEXT_BYTES, (result as ClipValidation.Valid).byteCount)
    }

    @Test
    fun rejectsOversizeUtf8() {
        val text = "a".repeat(ClipLimits.MAX_TEXT_BYTES + 1)
        val result = ClipValidator.validate(text)
        assertTrue(result is ClipValidation.TooLarge)
        assertEquals(ClipLimits.MAX_TEXT_BYTES + 1, (result as ClipValidation.TooLarge).byteCount)
    }

    @Test
    fun countsMultibyteCharacters() {
        // "ế" is more than one UTF-8 byte, so fewer characters can still exceed the limit.
        val text = "ế".repeat((ClipLimits.MAX_TEXT_BYTES / 2) + 1)
        val result = ClipValidator.validate(text)
        assertTrue(result is ClipValidation.TooLarge)
    }
}
