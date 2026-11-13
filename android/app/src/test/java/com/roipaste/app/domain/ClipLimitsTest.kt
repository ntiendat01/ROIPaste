package com.roipaste.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipLimitsTest {
    @Test
    fun maxTextBytesIs256KiB() {
        assertEquals(256 * 1024, ClipLimits.MAX_TEXT_BYTES)
    }

    @Test
    fun acceptsExactLimit() {
        assertTrue(ClipLimits.isWithinLimit(ClipLimits.MAX_TEXT_BYTES))
    }

    @Test
    fun rejectsOversizePayload() {
        assertFalse(ClipLimits.isWithinLimit(ClipLimits.MAX_TEXT_BYTES + 1))
    }

    @Test
    fun rejectsNegativeCount() {
        assertFalse(ClipLimits.isWithinLimit(-1))
    }
}
