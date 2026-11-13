package com.roipaste.app.fakes

import com.roipaste.app.data.clipboard.ClipboardSource
import com.roipaste.app.data.clipboard.SourceClip

class FakeClipboardSource : ClipboardSource {
    var current: SourceClip? = null
    var throwSecurityOnRead: Boolean = false
    var throwSecurityOnWrite: Boolean = false
    var reads: Int = 0
    var writes: Int = 0
    var lastSensitive: Boolean? = null

    override fun readPrimary(): SourceClip? {
        reads += 1
        if (throwSecurityOnRead) {
            throw SecurityException("denied")
        }
        return current
    }

    override fun writePlainText(label: String, text: String, sensitive: Boolean) {
        writes += 1
        lastSensitive = sensitive
        if (throwSecurityOnWrite) {
            throw SecurityException("denied")
        }
        current = SourceClip(text = text, mimeTypes = listOf("text/plain"), coercedText = text)
    }
}
