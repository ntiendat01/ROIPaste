package com.roipaste.app.domain

import kotlinx.coroutines.flow.Flow

interface SessionStore {
    val token: Flow<String?>

    val syncPaused: Flow<Boolean>

    val baseUrl: String

    fun currentToken(): String?

    suspend fun setToken(token: String?)

    suspend fun setSyncPaused(paused: Boolean)

    suspend fun setBaseUrl(url: String)
}
