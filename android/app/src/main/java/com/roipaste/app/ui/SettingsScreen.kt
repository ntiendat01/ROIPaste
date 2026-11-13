package com.roipaste.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.roipaste.app.R

@Composable
fun SettingsScreen(
    versionName: String,
    hasToken: Boolean,
    autoCopyWhileOpen: Boolean,
    notificationPreviews: Boolean,
    onToggleAutoCopy: (Boolean) -> Unit,
    onToggleNotificationPreviews: (Boolean) -> Unit,
    onUnpair: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.settings_version, versionName), style = MaterialTheme.typography.bodyLarge)
            Text(text = stringResource(R.string.settings_usb), style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.toggle_auto_copy), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = autoCopyWhileOpen, onCheckedChange = onToggleAutoCopy)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.toggle_notification_previews), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = notificationPreviews, onCheckedChange = onToggleNotificationPreviews)
            }
            Text(text = stringResource(R.string.settings_notifications_note), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = onUnpair,
                enabled = hasToken,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.action_unpair))
            }
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}
