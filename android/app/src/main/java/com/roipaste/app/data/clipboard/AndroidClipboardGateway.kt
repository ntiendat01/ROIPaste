package com.roipaste.app.data.clipboard

import com.roipaste.app.domain.ClipLimits
import com.roipaste.app.domain.FocusChecker

class AndroidClipboardGateway(
    private val source: ClipboardSource,
    private val focusChecker: FocusChecker,
) : ClipboardGateway {
    override fun readText(): ClipboardRead {
        if (!focusChecker.canReadClipboard()) {
            return ClipboardRead.NotFocused
        }
        return try {
            interpret(source.readPrimary())
        } catch (_: SecurityException) {
            ClipboardRead.AccessDenied
        }
    }

    override fun writeText(text: String, sensitive: Boolean): ClipboardWrite {
        return try {
            source.writePlainText(LABEL, text, sensitive)
            ClipboardWrite.Written
        } catch (_: SecurityException) {
            ClipboardWrite.AccessDenied
        } catch (_: RuntimeException) {
            ClipboardWrite.Failed
        }
    }

    private fun interpret(clip: SourceClip?): ClipboardRead {
        if (clip == null) {
            return ClipboardRead.Empty
        }
        val hasTextMime = clip.mimeTypes.any { mime ->
            mime.equals("text/plain", ignoreCase = true) || mime.startsWith("text/")
        }
        val value = clip.text ?: clip.coercedText
        if (value.isNullOrEmpty()) {
            return if (hasTextMime) ClipboardRead.Empty else ClipboardRead.Unsupported
        }
        if (!hasTextMime && clip.text == null) {
            return ClipboardRead.Unsupported
        }
        if (ClipLimits.utf8Size(value) > ClipLimits.MAX_TEXT_BYTES) {
            return ClipboardRead.TooLarge
        }
        return ClipboardRead.Text(value)
    }

    companion object {
        const val LABEL = "ROIPaste"
    }
}
