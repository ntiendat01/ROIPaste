package com.roipaste.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.roipaste.app.R
import com.roipaste.app.presentation.ConnectionState

@Composable
fun ConnectionCard(
    connection: ConnectionState,
    transport: String,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusText = when (connection) {
        ConnectionState.Checking -> stringResource(R.string.connection_checking)
        ConnectionState.Connected ->
            if (transport == "lan") stringResource(R.string.connection_wifi) else stringResource(R.string.connection_usb)
        ConnectionState.Disconnected -> stringResource(R.string.connection_lost)
    }
    val dotColor = when (connection) {
        ConnectionState.Checking -> MaterialTheme.colorScheme.outline
        ConnectionState.Connected -> MaterialTheme.colorScheme.primary
        ConnectionState.Disconnected -> MaterialTheme.colorScheme.error
    }
    val statusLabel = stringResource(R.string.cd_connection_status, statusText)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                        .semantics { contentDescription = statusLabel },
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text = stringResource(R.string.device_alias_macbook), style = MaterialTheme.typography.titleMedium)
                    Text(text = statusText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onReconnect) {
                Text(stringResource(R.string.action_reconnect))
            }
        }
    }
}
