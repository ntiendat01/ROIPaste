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
import com.roipaste.app.data.network.PairCompleter
import com.roipaste.app.data.network.PairingClient
import com.roipaste.app.domain.TransferRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private val pairingClient: PairCompleter = PairingClient(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val uiActive: () -> Boolean = { true },
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var reconnectJob: Job? = null
    private val pairing = HomePairing(
        sessionStore = sessionStore,
        pairingClient = pairingClient,
        clock = clock,
        ioDispatcher = ioDispatcher,
        state = _state,
        scope = viewModelScope,
        onPaired = { reconnect() },
    )
    private val receive = HomeReceive(
        repository = repository,
        clipboard = clipboard,
        state = _state,
        scope = viewModelScope,
        ioDispatcher = ioDispatcher,
        uiActive = uiActive,
    )

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
        viewModelScope.launch {
            sessionStore.autoCopyWhileOpen.collect { enabled ->
                _state.update { it.copy(autoCopyWhileOpen = enabled) }
            }
        }
        viewModelScope.launch {
            sessionStore.notificationPreviews.collect { enabled ->
                _state.update { it.copy(notificationPreviews = enabled) }
            }
        }
        reconnect()
    }

    fun onForeground() {
        if (_state.value.connection == ConnectionState.Connected) {
            receive.start()
        }
        viewModelScope.launch { receive.fetchLatestOnResume() }
    }

    fun onBackground() {
        receive.stop()
    }

    fun setAutoCopyWhileOpen(enabled: Boolean) {
        viewModelScope.launch { sessionStore.setAutoCopyWhileOpen(enabled) }
    }

    fun setNotificationPreviews(enabled: Boolean) {
        viewModelScope.launch { sessionStore.setNotificationPreviews(enabled) }
    }

    fun reconnect() {
        viewModelScope.launch { attemptConnection() }
    }

    fun reconnectWithBackoff() {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            val delaysMs = listOf(0L, 1_000L, 2_000L, 4_000L)
            for ((index, wait) in delaysMs.withIndex()) {
                if (index > 0) {
                    if (!uiActive()) {
                        return@launch
                    }
                    delay(wait)
                    if (!uiActive()) {
                        return@launch
                    }
                }
                if (attemptConnection()) {
                    return@launch
                }
            }
        }
    }

    private suspend fun attemptConnection(): Boolean {
        _state.update { it.copy(connection = ConnectionState.Checking) }
        val health = withContext(ioDispatcher) { repository.health() }
        if (health.isFailure) {
            val error = health.exceptionOrNull()
            val banner = if (error != null) ErrorMapping.bannerFor(error, sending = false) else Banner.Disconnected
            _state.update { it.copy(connection = ConnectionState.Disconnected, banner = banner) }
            return false
        }
        if (sessionStore.currentToken().isNullOrBlank()) {
            _state.update { it.copy(connection = ConnectionState.Disconnected) }
            return false
        }
        val status = withContext(ioDispatcher) { repository.status() }
        return status.fold(
            onSuccess = { mac ->
                _state.update {
                    it.copy(
                        connection = ConnectionState.Connected,
                        transport = mac.transport,
                        banner = if (it.banner is Banner.Disconnected || it.banner is Banner.PinMismatch) {
                            Banner.Ready
                        } else {
                            it.banner ?: Banner.Ready
                        },
                    )
                }
                if (uiActive()) {
                    receive.start()
                }
                true
            },
            onFailure = { error ->
                _state.update {
                    it.copy(connection = ConnectionState.Disconnected, banner = ErrorMapping.bannerFor(error, sending = false))
                }
                false
            },
        )
    }

    fun selectPairMethod(method: PairMethod) = pairing.selectMethod(method)

    fun updateWifiPayload(value: String) = pairing.updateWifiPayload(value)

    fun updateAlias(value: String) = pairing.updateAlias(value)

    fun submitWifiPayload() = pairing.submitWifiPayload()

    fun cancelWifiConfirm() = pairing.cancelWifiConfirm()

    fun confirmWifiPair() = pairing.confirmWifiPair()

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
            sessionStore.setUsbSession(trimmed)
            _state.update { it.copy(screen = HomeScreenKind.Home, tokenInput = "", pairError = false, transport = "usb") }
            reconnect()
        }
    }

    fun unpair() {
        viewModelScope.launch {
            sessionStore.clearPairing()
            receive.reset()
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
