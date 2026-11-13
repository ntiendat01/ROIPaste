package com.roipaste.app.domain

import kotlinx.coroutines.flow.Flow

interface SessionStore {
    val token: Flow<String?>

    val syncPaused: Flow<Boolean>

    val baseUrl: String

    fun currentToken(): String?

    fun currentPin(): String?

    fun currentTransport(): String

    fun currentDeviceId(): String?

    suspend fun setToken(token: String?)

    suspend fun setSyncPaused(paused: Boolean)

    suspend fun setBaseUrl(url: String)

    suspend fun setUsbSession(token: String)

    suspend fun setLanSession(
        credential: String,
        baseUrl: String,
        pin: String,
        deviceId: String,
        alias: String,
    )

    suspend fun clearPairing()
}
