package com.roipaste.app.domain

class PauseSync(
    private val sessionStore: SessionStore,
) {
    suspend operator fun invoke(paused: Boolean) {
        sessionStore.setSyncPaused(paused)
    }
}
