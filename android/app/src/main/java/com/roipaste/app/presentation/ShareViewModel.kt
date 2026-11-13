package com.roipaste.app.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roipaste.app.domain.SendClip
import com.roipaste.app.domain.SessionStore
import com.roipaste.app.domain.ShareValidation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ShareViewModel(
    validation: ShareValidation,
    private val sendClip: SendClip,
    private val sessionStore: SessionStore,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(initialState(validation, sessionStore.currentToken()))
    val state: StateFlow<ShareUiState> = _state.asStateFlow()

    fun togglePreview() {
        if (_state.value.text.isEmpty()) {
            return
        }
        _state.update { it.copy(previewVisible = !it.previewVisible) }
    }

    fun confirmSend() {
        val current = _state.value
        if (current.sending || current.sent || current.invalid) {
            return
        }
        if (sessionStore.currentToken().isNullOrBlank()) {
            _state.update { it.copy(needsPairing = true, error = Banner.Unauthenticated) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            val result = withContext(ioDispatcher) { sendClip(current.text) }
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(sending = false, sent = true, error = null) },
                    onFailure = { error ->
                        state.copy(sending = false, sent = false, error = ErrorMapping.bannerFor(error, sending = true))
                    },
                )
            }
        }
    }

    companion object {
        fun initialState(validation: ShareValidation, token: String?): ShareUiState {
            return when (validation) {
                is ShareValidation.Valid -> ShareUiState(
                    text = validation.text,
                    needsPairing = token.isNullOrBlank(),
                    error = if (token.isNullOrBlank()) Banner.Unauthenticated else null,
                )
                else -> ShareUiState(invalid = true)
            }
        }
    }
}
