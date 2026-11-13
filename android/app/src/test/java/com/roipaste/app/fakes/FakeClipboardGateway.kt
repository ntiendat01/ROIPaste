package com.roipaste.app.fakes

import com.roipaste.app.data.clipboard.ClipboardGateway
import com.roipaste.app.data.clipboard.ClipboardRead
import com.roipaste.app.data.clipboard.ClipboardWrite

class FakeClipboardGateway : ClipboardGateway {
    var read: ClipboardRead = ClipboardRead.Empty
    var writeResult: ClipboardWrite = ClipboardWrite.Written
    var reads: Int = 0
    var writes: Int = 0
    var lastWritten: String? = null
    var lastSensitive: Boolean? = null

    override fun readText(): ClipboardRead {
        reads += 1
        return read
    }

    override fun writeText(text: String, sensitive: Boolean): ClipboardWrite {
        writes += 1
        lastWritten = text
        lastSensitive = sensitive
        return writeResult
    }
}
