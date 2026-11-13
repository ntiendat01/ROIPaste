package com.roipaste.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roipaste.app.data.clipboard.ClipboardGateway
import com.roipaste.app.data.clipboard.ClipboardRead
import com.roipaste.app.data.clipboard.ClipboardWrite
import com.roipaste.app.domain.FetchClip
import com.roipaste.app.domain.PauseSync
import com.roipaste.app.domain.SendClip
import com.roipaste.app.domain.SessionStore
import com.roipaste.app.domain.TransferRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val sendClip: SendClip,
    private val fetchClip: FetchClip,
    private val pauseSync: PauseSync,
    private val clipboard: ClipboardGateway,
    private val sessionStore: SessionStore,
    private val repository: TransferRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.token.collect { token ->
                _state.update { it.copy(hasToken = !token.isNullOrBlank()) }
            }
        }
        viewModelScope.launch {
            sessionStore.syncPaused.collect { paused ->
                _state.update { it.copy(syncPaused = paused) }
            }
        }
        reconnect()
    }

    fun reconnect() {
        viewModelScope.launch {
            _state.update { it.copy(connection = ConnectionState.Checking) }
            val health = withContext(ioDispatcher) { repository.health() }
            if (health.isFailure) {
                _state.update { it.copy(connection = ConnectionState.Disconnected, banner = Banner.Disconnected) }
                return@launch
            }
            if (sessionStore.currentToken().isNullOrBlank()) {
                _state.update { it.copy(connection = ConnectionState.Disconnected) }
                return@launch
            }
            val status = withContext(ioDispatcher) { repository.status() }
            status.fold(
                onSuccess = { mac ->
                    _state.update {
                        it.copy(
                            connection = ConnectionState.Connected,
                            transport = mac.transport,
                            banner = if (it.banner is Banner.Disconnected) Banner.Ready else it.banner ?: Banner.Ready,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(connection = ConnectionState.Disconnected, banner = ErrorMapping.bannerFor(error, sending = false))
                    }
                },
            )
        }
    }

    private var lastTransfer: TransferKind? = null

    fun retry() {
        when (lastTransfer) {
            TransferKind.Send -> send()
            TransferKind.Fetch -> fetch()
            null -> reconnect()
        }
    }

    fun send() {
        val current = _state.value
        if (current.sending || current.fetching) {
            return
        }
        if (current.syncPaused) {
            _state.update { it.copy(banner = Banner.SyncPaused) }
            return
        }
        lastTransfer = TransferKind.Send
        viewModelScope.launch { performSend() }
    }

    fun fetch() {
        val current = _state.value
        if (current.sending || current.fetching) {
            return
        }
        if (current.syncPaused) {
            _state.update { it.copy(banner = Banner.SyncPaused) }
            return
        }
        lastTransfer = TransferKind.Fetch
        viewModelScope.launch { performFetch() }
    }

    fun copyToAndroid() {
        val text = _state.value.previewText
        if (text.isNullOrEmpty()) {
            _state.update { it.copy(banner = Banner.NothingToCopy) }
            return
        }
        when (clipboard.writeText(text, sensitive = true)) {
            ClipboardWrite.Written -> _state.update { it.copy(banner = Banner.Copied) }
            ClipboardWrite.AccessDenied -> _state.update { it.copy(banner = Banner.AccessDenied) }
            ClipboardWrite.Failed -> _state.update { it.copy(banner = Banner.CopyFailed) }
        }
    }

    fun togglePreview() {
        if (_state.value.previewText.isNullOrEmpty()) {
            return
        }
        _state.update { it.copy(previewVisible = !it.previewVisible) }
    }

    fun setSyncPaused(paused: Boolean) {
        viewModelScope.launch { pauseSync(paused) }
    }

    fun openPair() {
        _state.update { it.copy(screen = HomeScreenKind.Pair, pairError = false) }
    }

    fun openSettings() {
        _state.update { it.copy(screen = HomeScreenKind.Settings) }
    }

    fun closeOverlay() {
        _state.update { it.copy(screen = HomeScreenKind.Home, pairError = false) }
    }

    fun updateTokenInput(value: String) {
        _state.update { it.copy(tokenInput = value, pairError = false) }
    }

    fun saveToken() {
        val trimmed = _state.value.tokenInput.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(pairError = true) }
            return
        }
        viewModelScope.launch {
            sessionStore.setToken(trimmed)
            _state.update { it.copy(screen = HomeScreenKind.Home, tokenInput = "", pairError = false) }
            reconnect()
        }
    }

    fun unpair() {
        viewModelScope.launch {
            sessionStore.setToken(null)
            _state.update {
                it.copy(
                    screen = HomeScreenKind.Home,
                    connection = ConnectionState.Disconnected,
                    hasToken = false,
                )
            }
        }
    }

    private suspend fun performSend() {
        _state.update { it.copy(sending = true) }
        val read = clipboard.readText()
        if (read !is ClipboardRead.Text) {
            _state.update { it.copy(sending = false, banner = ErrorMapping.fromClipboard(read) ?: Banner.SendFailed) }
            return
        }
        val result = withContext(ioDispatcher) { sendClip(read.value) }
        _state.update { state ->
            result.fold(
                onSuccess = { state.copy(sending = false, banner = Banner.Sent(clock()), connection = ConnectionState.Connected) },
                onFailure = { error -> state.copy(sending = false, banner = ErrorMapping.bannerFor(error, sending = true)) },
            )
        }
    }

    private suspend fun performFetch() {
        val previous = _state.value.previewText
        val previousBytes = _state.value.previewByteCount
        _state.update { it.copy(fetching = true) }
        val result = withContext(ioDispatcher) { fetchClip() }
        _state.update { state ->
            result.fold(
                onSuccess = { clip ->
                    if (clip == null) {
                        state.copy(fetching = false, banner = Banner.ClipboardEmpty)
                    } else {
                        state.copy(
                            fetching = false,
                            previewText = clip.text,
                            previewByteCount = clip.byteCount,
                            previewVisible = false,
                            banner = Banner.Ready,
                            connection = ConnectionState.Connected,
                        )
                    }
                },
                onFailure = { error ->
                    state.copy(
                        fetching = false,
                        previewText = previous,
                        previewByteCount = previousBytes,
                        banner = ErrorMapping.bannerFor(error, sending = false),
                    )
                },
            )
        }
    }
}

private enum class TransferKind {
    Send,
    Fetch,
}
