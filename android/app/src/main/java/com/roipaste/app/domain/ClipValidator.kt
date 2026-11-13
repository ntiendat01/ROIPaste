package com.roipaste.app.domain

sealed class ClipValidation {
    data class Valid(val text: String, val byteCount: Int) : ClipValidation()

    data object Empty : ClipValidation()

    data class TooLarge(val byteCount: Int) : ClipValidation()
}

object ClipValidator {
    fun validate(text: String?): ClipValidation {
        if (text.isNullOrEmpty()) {
            return ClipValidation.Empty
        }
        val byteCount = ClipLimits.utf8Size(text)
        if (byteCount > ClipLimits.MAX_TEXT_BYTES) {
            return ClipValidation.TooLarge(byteCount)
        }
        return ClipValidation.Valid(text, byteCount)
    }
}
