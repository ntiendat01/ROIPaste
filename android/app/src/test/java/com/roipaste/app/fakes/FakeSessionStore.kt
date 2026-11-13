package com.roipaste.app.fakes

import com.roipaste.app.domain.SessionStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeSessionStore(
    token: String? = "test-token",
    paused: Boolean = false,
    override var baseUrl: String = "http://127.0.0.1:5000",
) : SessionStore {
    private val tokenState = MutableStateFlow(token)
    private val pauseState = MutableStateFlow(paused)

    override val token: StateFlow<String?> = tokenState.asStateFlow()
    override val syncPaused: StateFlow<Boolean> = pauseState.asStateFlow()

    override fun currentToken(): String? = tokenState.value

    override suspend fun setToken(token: String?) {
        tokenState.value = token?.takeIf { it.isNotBlank() }
    }

    override suspend fun setSyncPaused(paused: Boolean) {
        pauseState.value = paused
    }

    override suspend fun setBaseUrl(url: String) {
        baseUrl = url.trim().trimEnd('/')
    }
}
