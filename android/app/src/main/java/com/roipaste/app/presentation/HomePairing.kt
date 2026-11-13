package com.roipaste.app.presentation

import com.roipaste.app.data.network.PairCompleter
import com.roipaste.app.domain.PairingParse
import com.roipaste.app.domain.PairingPayloadParser
import com.roipaste.app.domain.SessionStore
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomePairing(
    private val sessionStore: SessionStore,
    private val pairingClient: PairCompleter,
    private val clock: () -> Long,
    private val ioDispatcher: CoroutineDispatcher,
    private val state: MutableStateFlow<HomeUiState>,
    private val scope: CoroutineScope,
    private val onPaired: () -> Unit,
) {
    fun selectMethod(method: PairMethod) {
        state.update { it.copy(pairMethod = method, pairError = false, pendingWifi = null) }
    }

    fun updateWifiPayload(value: String) {
        state.update { it.copy(wifiPayloadInput = value, pairError = false) }
    }

    fun updateAlias(value: String) {
        state.update { it.copy(aliasInput = value) }
    }

    fun submitWifiPayload() {
        val now = clock() / 1000
        when (val parsed = PairingPayloadParser.parse(state.value.wifiPayloadInput, now)) {
            is PairingParse.Valid -> state.update { it.copy(pendingWifi = parsed.payload, pairError = false) }
            PairingParse.Expired -> state.update { it.copy(pairError = true, banner = Banner.PairingExpired, pendingWifi = null) }
            PairingParse.MissingPin, PairingParse.Malformed, PairingParse.UnsupportedVersion ->
                state.update { it.copy(pairError = true, pendingWifi = null, banner = Banner.PairingFailed) }
        }
    }

    fun cancelWifiConfirm() {
        state.update { it.copy(pendingWifi = null) }
    }

    fun confirmWifiPair() {
        val payload = state.value.pendingWifi ?: return
        if (state.value.pairingBusy) {
            return
        }
        val alias = state.value.aliasInput.trim().ifEmpty { "Ace 5" }
        val deviceId = sessionStore.currentDeviceId() ?: UUID.randomUUID().toString()
        scope.launch {
            state.update { it.copy(pairingBusy = true) }
            val result = withContext(ioDispatcher) { pairingClient.complete(payload, deviceId, alias) }
            result.fold(
                onSuccess = { response ->
                    sessionStore.setLanSession(response.credential, payload.baseUrl, payload.pin, deviceId, alias)
                    state.update {
                        it.copy(
                            pairingBusy = false,
                            pendingWifi = null,
                            wifiPayloadInput = "",
                            screen = HomeScreenKind.Home,
                            hasToken = true,
                            transport = "lan",
                            banner = Banner.Ready,
                        )
                    }
                    onPaired()
                },
                onFailure = { error ->
                    state.update {
                        it.copy(pairingBusy = false, banner = ErrorMapping.bannerFor(error, sending = false))
                    }
                },
            )
        }
    }
}
