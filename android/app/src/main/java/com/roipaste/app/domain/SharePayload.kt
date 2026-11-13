package com.roipaste.app.domain

import android.content.Intent

sealed class ShareValidation {
    data class Valid(val text: String) : ShareValidation()

    data object InvalidAction : ShareValidation()

    data object UnsupportedType : ShareValidation()

    data object Empty : ShareValidation()

    data class TooLarge(val byteCount: Int) : ShareValidation()
}

object SharePayload {
    fun parse(action: String?, mimeType: String?, text: String?): ShareValidation {
        if (action != Intent.ACTION_SEND) {
            return ShareValidation.InvalidAction
        }
        if (mimeType == null || !mimeType.startsWith("text/plain")) {
            return ShareValidation.UnsupportedType
        }
        return when (val validation = ClipValidator.validate(text)) {
            is ClipValidation.Valid -> ShareValidation.Valid(validation.text)
            is ClipValidation.Empty -> ShareValidation.Empty
            is ClipValidation.TooLarge -> ShareValidation.TooLarge(validation.byteCount)
        }
    }
}
