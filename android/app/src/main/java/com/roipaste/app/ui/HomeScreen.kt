package com.roipaste.app.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.roipaste.app.R
import com.roipaste.app.presentation.Banner
import com.roipaste.app.presentation.ConnectionState
import com.roipaste.app.presentation.HomeUiState
import com.roipaste.app.ui.theme.RoiPasteTheme

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSend: () -> Unit,
    onFetch: () -> Unit,
    onCopy: () -> Unit,
    onTogglePreview: () -> Unit,
    onReconnect: () -> Unit,
    onTogglePause: (Boolean) -> Unit,
    onOpenPair: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
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
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                text = stringResource(R.string.subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ConnectionCard(connection = state.connection, transport = state.transport, onReconnect = onReconnect)
            StatusBanner(banner = state.banner, onRetry = onRetry)
            Button(
                onClick = onSend,
                enabled = !state.sending && !state.fetching,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(if (state.sending) stringResource(R.string.action_sending) else stringResource(R.string.action_send))
            }
            OutlinedButton(
                onClick = onFetch,
                enabled = !state.sending && !state.fetching,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(if (state.fetching) stringResource(R.string.action_fetching) else stringResource(R.string.action_fetch))
            }
            ReceivedPreview(text = state.previewText, visible = state.previewVisible, onToggle = onTogglePreview)
            OutlinedButton(
                onClick = onCopy,
                enabled = !state.previewText.isNullOrEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_copy))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.toggle_pause_sync), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.syncPaused, onCheckedChange = onTogglePause)
            }
            TextButton(onClick = onOpenPair, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_pair))
            }
            TextButton(onClick = onOpenSettings, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_settings))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun StatusBanner(banner: Banner?, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    val text = when (banner) {
        null -> stringResource(R.string.status_ready)
        is Banner.Sent -> stringResource(R.string.status_sent_with_time, BannerMessages.formatTime(banner.atMillis))
        else -> stringResource(BannerMessages.messageRes(banner))
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        if (onRetry != null && banner.isRetryable()) {
            TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }
}

private fun Banner?.isRetryable(): Boolean {
    return this is Banner.Disconnected ||
        this is Banner.Timeout ||
        this is Banner.SendFailed ||
        this is Banner.FetchFailed ||
        this is Banner.Unauthenticated ||
        this is Banner.Forbidden ||
        this is Banner.PinMismatch ||
        this is Banner.InsecureTransport
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    RoiPasteTheme {
        HomeScreen(
            state = HomeUiState(connection = ConnectionState.Connected, banner = Banner.Ready),
            onSend = {},
            onFetch = {},
            onCopy = {},
            onTogglePreview = {},
            onReconnect = {},
            onTogglePause = {},
            onOpenPair = {},
            onOpenSettings = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeScreenDarkPreview() {
    RoiPasteTheme {
        HomeScreen(
            state = HomeUiState(connection = ConnectionState.Disconnected, banner = Banner.Disconnected),
            onSend = {},
            onFetch = {},
            onCopy = {},
            onTogglePreview = {},
            onReconnect = {},
            onTogglePause = {},
            onOpenPair = {},
            onOpenSettings = {},
            onRetry = {},
        )
    }
}
