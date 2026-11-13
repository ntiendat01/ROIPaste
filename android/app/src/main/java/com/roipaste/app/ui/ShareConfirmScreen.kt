package com.roipaste.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roipaste.app.R
import com.roipaste.app.presentation.Banner
import com.roipaste.app.presentation.ShareUiState

@Composable
fun ShareConfirmScreen(
    state: ShareUiState,
    onTogglePreview: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.share_title), style = MaterialTheme.typography.headlineMedium)
            when {
                state.invalid -> Text(stringResource(R.string.share_invalid), style = MaterialTheme.typography.bodyLarge)
                state.sent -> Text(stringResource(R.string.share_sent), style = MaterialTheme.typography.bodyLarge)
                else -> {
                    Text(stringResource(R.string.share_confirm_prompt), style = MaterialTheme.typography.bodyLarge)
                    if (state.needsPairing) {
                        Text(stringResource(R.string.share_need_pair), color = MaterialTheme.colorScheme.error)
                    }
                    ReceivedPreview(text = state.text, visible = state.previewVisible, onToggle = onTogglePreview)
                    state.error?.let { error ->
                        val message = if (error is Banner.Sent) {
                            stringResource(R.string.status_sent)
                        } else {
                            stringResource(BannerMessages.messageRes(error))
                        }
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (!state.invalid && !state.sent) {
                Button(
                    onClick = onConfirm,
                    enabled = !state.sending && !state.needsPairing,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(if (state.sending) stringResource(R.string.share_sending) else stringResource(R.string.action_confirm_send))
                }
            }
            TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}
