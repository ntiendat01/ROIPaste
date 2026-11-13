package com.roipaste.app.fakes

import com.roipaste.app.domain.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSessionStore(
    token: String? = "test-token",
    paused: Boolean = false,
    override var baseUrl: String = "http://127.0.0.1:5000",
    private var pin: String? = null,
    private var transport: String = "usb",
    private var deviceId: String? = null,
) : SessionStore {
    private val tokenState = MutableStateFlow(token)
    private val pauseState = MutableStateFlow(paused)

    override val token: StateFlow<String?> = tokenState.asStateFlow()
    override val syncPaused: StateFlow<Boolean> = pauseState.asStateFlow()

    var clearCount: Int = 0

    override fun currentToken(): String? = tokenState.value

    override fun currentPin(): String? = pin

    override fun currentTransport(): String = transport

    override fun currentDeviceId(): String? = deviceId

    override suspend fun setToken(token: String?) {
        tokenState.value = token?.takeIf { it.isNotBlank() }
    }

    override suspend fun setSyncPaused(paused: Boolean) {
        pauseState.value = paused
    }

    override suspend fun setBaseUrl(url: String) {
        baseUrl = url.trim().trimEnd('/')
    }

    override suspend fun setUsbSession(token: String) {
        tokenState.value = token
        baseUrl = "http://127.0.0.1:5000"
        pin = null
        transport = "usb"
    }

    override suspend fun setLanSession(
        credential: String,
        baseUrl: String,
        pin: String,
        deviceId: String,
        alias: String,
    ) {
        tokenState.value = credential
        this.baseUrl = baseUrl.trim().trimEnd('/')
        this.pin = pin
        this.transport = "lan"
        this.deviceId = deviceId
    }

    override suspend fun clearPairing() {
        clearCount += 1
        tokenState.value = null
        pin = null
        transport = "usb"
        deviceId = null
        baseUrl = "http://127.0.0.1:5000"
    }
}
