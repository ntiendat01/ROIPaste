package com.roipaste.app.data.clipboard

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

class SystemClipboardSource(
    private val clipboard: ClipboardManager,
    private val context: Context,
) : ClipboardSource {
    override fun readPrimary(): SourceClip? {
        val data = clipboard.primaryClip ?: return null
        if (data.itemCount < 1) {
            return SourceClip(text = null, mimeTypes = mimeTypes(data.description), coercedText = null)
        }
        val item = data.getItemAt(0)
        return SourceClip(
            text = item.text?.toString(),
            mimeTypes = mimeTypes(data.description),
            coercedText = item.coerceToText(context)?.toString(),
        )
    }

    override fun writePlainText(label: String, text: String, sensitive: Boolean) {
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
    }

    private fun mimeTypes(description: ClipDescription?): List<String> {
        if (description == null) {
            return emptyList()
        }
        return (0 until description.mimeTypeCount).map { index -> description.getMimeType(index) }
    }
}
