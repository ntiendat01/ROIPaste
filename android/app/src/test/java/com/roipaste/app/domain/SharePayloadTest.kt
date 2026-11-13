package com.roipaste.app.domain

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SharePayloadTest {
    @Test
    fun acceptsPlainTextSend() {
        val result = SharePayload.parse(Intent.ACTION_SEND, "text/plain", "hello")
        assertEquals(ShareValidation.Valid("hello"), result)
    }

    @Test
    fun acceptsPlainTextWithCharset() {
        val result = SharePayload.parse(Intent.ACTION_SEND, "text/plain; charset=utf-8", "hello")
        assertEquals(ShareValidation.Valid("hello"), result)
    }

    @Test
    fun rejectsOtherActions() {
        assertEquals(ShareValidation.InvalidAction, SharePayload.parse(Intent.ACTION_VIEW, "text/plain", "hello"))
        assertEquals(ShareValidation.InvalidAction, SharePayload.parse(null, "text/plain", "hello"))
    }

    @Test
    fun rejectsNonTextMime() {
        assertEquals(ShareValidation.UnsupportedType, SharePayload.parse(Intent.ACTION_SEND, "image/png", "hello"))
        assertEquals(ShareValidation.UnsupportedType, SharePayload.parse(Intent.ACTION_SEND, null, "hello"))
    }

    @Test
    fun rejectsEmptyText() {
        assertEquals(ShareValidation.Empty, SharePayload.parse(Intent.ACTION_SEND, "text/plain", ""))
        assertEquals(ShareValidation.Empty, SharePayload.parse(Intent.ACTION_SEND, "text/plain", null))
    }

    @Test
    fun rejectsOversizeText() {
        val text = "a".repeat(ClipLimits.MAX_TEXT_BYTES + 1)
        val result = SharePayload.parse(Intent.ACTION_SEND, "text/plain", text)
        assertTrue(result is ShareValidation.TooLarge)
    }
}
