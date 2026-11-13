package com.roipaste.app.ui

import androidx.annotation.StringRes
import com.roipaste.app.R
import com.roipaste.app.presentation.Banner
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BannerMessages {
    @StringRes
    fun messageRes(banner: Banner): Int {
        return when (banner) {
            Banner.Ready -> R.string.status_ready
            is Banner.Sent -> R.string.status_sent
            Banner.ClipboardEmpty -> R.string.status_clipboard_empty
            Banner.Disconnected -> R.string.status_disconnected
            Banner.FocusRequired -> R.string.status_focus_required
            Banner.Unsupported -> R.string.status_unsupported
            Banner.TooLarge -> R.string.status_too_large
            Banner.SyncPaused -> R.string.status_sync_paused
            Banner.Unauthenticated -> R.string.status_unauthenticated
            Banner.Forbidden -> R.string.status_forbidden
            Banner.Timeout -> R.string.status_timeout
            Banner.SendFailed -> R.string.status_send_failed
            Banner.FetchFailed -> R.string.status_fetch_failed
            Banner.AccessDenied -> R.string.status_access_denied
            Banner.NothingToCopy -> R.string.status_nothing_to_copy
            Banner.Copied -> R.string.status_copied
            Banner.CopyFailed -> R.string.status_copy_failed
        }
    }

    fun formatTime(millis: Long): String {
        return TIME_FORMAT.withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(millis))
    }

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
}
