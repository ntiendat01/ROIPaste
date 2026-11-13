package com.roipaste.app.presentation

data class ShareUiState(
    val text: String = "",
    val previewVisible: Boolean = false,
    val sending: Boolean = false,
    val sent: Boolean = false,
    val invalid: Boolean = false,
    val needsPairing: Boolean = false,
    val error: Banner? = null,
)
