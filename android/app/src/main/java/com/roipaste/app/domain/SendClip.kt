package com.roipaste.app.domain

import java.util.UUID

class SendClip(
    private val repository: TransferRepository,
) {
    suspend operator fun invoke(text: String, messageId: String = UUID.randomUUID().toString()): Result<SendAck> {
        return when (val validation = ClipValidator.validate(text)) {
            is ClipValidation.Empty -> Result.failure(LocalClipError.Empty)
            is ClipValidation.TooLarge -> Result.failure(LocalClipError.TooLarge)
            is ClipValidation.Valid -> repository.sendClip(validation.text, messageId)
        }
    }
}

sealed class LocalClipError : Exception() {
    data object Empty : LocalClipError()

    data object TooLarge : LocalClipError()

    data object Unsupported : LocalClipError()

    data object NotFocused : LocalClipError()

    data object AccessDenied : LocalClipError()
}
