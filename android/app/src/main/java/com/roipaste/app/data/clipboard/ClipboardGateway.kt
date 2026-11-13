package com.roipaste.app.data.clipboard

interface ClipboardGateway {
    fun readText(): ClipboardRead

    fun writeText(text: String, sensitive: Boolean = false): ClipboardWrite
}

sealed class ClipboardRead {
    data class Text(val value: String) : ClipboardRead()

    data object Empty : ClipboardRead()

    data object NotFocused : ClipboardRead()

    data object Unsupported : ClipboardRead()

    data object TooLarge : ClipboardRead()

    data object AccessDenied : ClipboardRead()
}

sealed class ClipboardWrite {
    data object Written : ClipboardWrite()

    data object AccessDenied : ClipboardWrite()

    data object Failed : ClipboardWrite()
}
