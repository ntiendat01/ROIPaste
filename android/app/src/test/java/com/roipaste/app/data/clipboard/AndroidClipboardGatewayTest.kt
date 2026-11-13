package com.roipaste.app.data.clipboard

import com.roipaste.app.domain.ClipLimits
import com.roipaste.app.domain.MutableFocusChecker
import com.roipaste.app.fakes.FakeClipboardSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidClipboardGatewayTest {
    private val source = FakeClipboardSource()
    private val focus = MutableFocusChecker()
    private val gateway = AndroidClipboardGateway(source, focus)

    @Test
    fun doesNotTouchSourceWhenNotFocused() {
        source.current = SourceClip("secret", listOf("text/plain"), "secret")
        val result = gateway.readText()
        assertEquals(ClipboardRead.NotFocused, result)
        assertEquals(0, source.reads)
    }

    @Test
    fun readsPlainTextWhenResumedAndFocused() {
        focus.resumed = true
        focus.windowFocused = true
        source.current = SourceClip("hello", listOf("text/plain"), "hello")
        assertEquals(ClipboardRead.Text("hello"), gateway.readText())
        assertEquals(1, source.reads)
    }

    @Test
    fun treatsNullClipAsEmpty() {
        focus.resumed = true
        focus.windowFocused = true
        source.current = null
        assertEquals(ClipboardRead.Empty, gateway.readText())
    }

    @Test
    fun rejectsNonTextCoercion() {
        focus.resumed = true
        focus.windowFocused = true
        source.current = SourceClip(text = null, mimeTypes = listOf("image/png"), coercedText = "content://img")
        assertEquals(ClipboardRead.Unsupported, gateway.readText())
    }

    @Test
    fun rejectsOversizeText() {
        focus.resumed = true
        focus.windowFocused = true
        val text = "a".repeat(ClipLimits.MAX_TEXT_BYTES + 1)
        source.current = SourceClip(text, listOf("text/plain"), text)
        assertEquals(ClipboardRead.TooLarge, gateway.readText())
    }

    @Test
    fun mapsSecurityException() {
        focus.resumed = true
        focus.windowFocused = true
        source.throwSecurityOnRead = true
        assertEquals(ClipboardRead.AccessDenied, gateway.readText())
    }

    @Test
    fun writeRecordsSensitiveFlag() {
        assertEquals(ClipboardWrite.Written, gateway.writeText("copied", sensitive = true))
        assertEquals(1, source.writes)
        assertEquals(true, source.lastSensitive)
    }
}
