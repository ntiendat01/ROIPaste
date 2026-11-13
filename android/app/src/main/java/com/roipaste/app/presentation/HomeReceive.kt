package com.roipaste.app.presentation

import com.roipaste.app.data.clipboard.ClipboardGateway
import com.roipaste.app.data.clipboard.ClipboardWrite
import com.roipaste.app.domain.FetchedClip
import com.roipaste.app.domain.TransferError
import com.roipaste.app.domain.TransferRepository
import kotlin.math.max
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeReceive(
    private val repository: TransferRepository,
    private val clipboard: ClipboardGateway,
    private val state: MutableStateFlow<HomeUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val uiActive: () -> Boolean,
) {
    private var job: Job? = null
    private var since: Long = 0

    fun start() {
        if (job?.isActive == true) {
            return
        }
        job = scope.launch { loop() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun reset() {
        stop()
        since = 0
    }

    suspend fun fetchLatestOnResume() {
        if (!uiActive() || state.value.syncPaused) {
            return
        }
        val result = withContext(ioDispatcher) { repository.awaitNextClip(since, 0) }
        val clip = result.getOrNull() ?: return
        accept(clip)
    }

    private suspend fun loop() {
        val delaysMs = listOf(0L, 1_000L, 2_000L, 4_000L)
        var attempt = 0
        while (uiActive()) {
            if (state.value.connection != ConnectionState.Connected || state.value.syncPaused) {
                return
            }
            if (attempt > 0) {
                delay(delaysMs[attempt.coerceAtMost(delaysMs.lastIndex)])
                if (!uiActive()) {
                    return
                }
            }
            val result = withContext(ioDispatcher) {
                repository.awaitNextClip(since, WAIT_SECONDS)
            }
            result.fold(
                onSuccess = { clip ->
                    attempt = 0
                    if (clip != null) {
                        accept(clip)
                    }
                },
                onFailure = { error ->
                    if (error is TransferError.SyncPaused || error is TransferError.Timeout) {
                        attempt = 0
                    } else {
                        attempt = (attempt + 1).coerceAtMost(delaysMs.lastIndex)
                    }
                },
            )
        }
    }

    private fun accept(clip: FetchedClip) {
        since = max(since, clip.sequence)
        val shouldCopy = state.value.autoCopyWhileOpen && uiActive()
        val copyBanner = if (shouldCopy) bannerForWrite(clipboard.writeText(clip.text, sensitive = true)) else null
        state.update {
            it.copy(
                previewText = clip.text,
                previewByteCount = clip.byteCount,
                previewVisible = false,
                banner = copyBanner ?: Banner.Ready,
                connection = ConnectionState.Connected,
            )
        }
    }

    private fun bannerForWrite(result: ClipboardWrite): Banner {
        return when (result) {
            ClipboardWrite.Written -> Banner.Copied
            ClipboardWrite.AccessDenied -> Banner.AccessDenied
            ClipboardWrite.Failed -> Banner.CopyFailed
        }
    }

    companion object {
        const val WAIT_SECONDS = 25
    }
}
