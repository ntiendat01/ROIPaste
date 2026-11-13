package com.roipaste.app.presentation

import com.roipaste.app.data.clipboard.ClipboardRead
import com.roipaste.app.domain.LocalClipError
import com.roipaste.app.domain.TransferError

object ErrorMapping {
    fun bannerFor(error: Throwable, sending: Boolean): Banner {
        return when (error) {
            LocalClipError.Empty -> Banner.ClipboardEmpty
            LocalClipError.TooLarge -> Banner.TooLarge
            LocalClipError.Unsupported -> Banner.Unsupported
            LocalClipError.NotFocused -> Banner.FocusRequired
            LocalClipError.AccessDenied -> Banner.AccessDenied
            TransferError.Unauthenticated -> Banner.Unauthenticated
            TransferError.Forbidden -> Banner.Forbidden
            TransferError.PayloadTooLarge -> Banner.TooLarge
            TransferError.Timeout -> Banner.Timeout
            TransferError.Disconnected -> Banner.Disconnected
            TransferError.Unavailable, TransferError.InvalidResponse -> {
                if (sending) Banner.SendFailed else Banner.FetchFailed
            }
            is TransferError.Protocol -> {
                if (sending) Banner.SendFailed else Banner.FetchFailed
            }
            else -> if (sending) Banner.SendFailed else Banner.FetchFailed
        }
    }

    fun fromClipboard(read: ClipboardRead): Banner? {
        return when (read) {
            is ClipboardRead.Text -> null
            ClipboardRead.Empty -> Banner.ClipboardEmpty
            ClipboardRead.NotFocused -> Banner.FocusRequired
            ClipboardRead.Unsupported -> Banner.Unsupported
            ClipboardRead.TooLarge -> Banner.TooLarge
            ClipboardRead.AccessDenied -> Banner.AccessDenied
        }
    }
}
