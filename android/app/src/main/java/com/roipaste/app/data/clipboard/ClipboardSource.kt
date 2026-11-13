package com.roipaste.app.data.clipboard

data class SourceClip(
    val text: String?,
    val mimeTypes: List<String>,
    val coercedText: String?,
)

interface ClipboardSource {
    fun readPrimary(): SourceClip?

    fun writePlainText(label: String, text: String, sensitive: Boolean)
}
