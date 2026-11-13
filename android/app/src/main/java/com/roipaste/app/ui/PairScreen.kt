package com.roipaste.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.roipaste.app.R
import com.roipaste.app.domain.PairingPayload
import com.roipaste.app.presentation.HomeUiState
import com.roipaste.app.presentation.PairMethod

@Composable
fun PairScreen(
    state: HomeUiState,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onSelectMethod: (PairMethod) -> Unit,
    onWifiPayloadChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onSubmitWifi: () -> Unit,
    onScanQr: () -> Unit,
    onConfirmWifi: () -> Unit,
    onCancelWifi: () -> Unit,
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
            Text(text = stringResource(R.string.pair_title), style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.pairMethod == PairMethod.Usb,
                    onClick = { onSelectMethod(PairMethod.Usb) },
                    label = { Text(stringResource(R.string.pair_method_usb)) },
                )
                FilterChip(
                    selected = state.pairMethod == PairMethod.Wifi,
                    onClick = { onSelectMethod(PairMethod.Wifi) },
                    label = { Text(stringResource(R.string.pair_method_wifi)) },
                )
            }
            if (state.pairMethod == PairMethod.Usb) {
                UsbPairForm(state = state, onTokenChange = onTokenChange, onSave = onSave)
            } else {
                WifiPairForm(
                    state = state,
                    onWifiPayloadChange = onWifiPayloadChange,
                    onAliasChange = onAliasChange,
                    onSubmitWifi = onSubmitWifi,
                    onScanQr = onScanQr,
                    onConfirmWifi = onConfirmWifi,
                    onCancelWifi = onCancelWifi,
                )
            }
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}

@Composable
private fun UsbPairForm(
    state: HomeUiState,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Text(text = stringResource(R.string.pair_usb_intro), style = MaterialTheme.typography.bodyMedium)
    Text(text = stringResource(R.string.pair_usb_steps), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = state.tokenInput,
        onValueChange = onTokenChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.pair_token_label)) },
        singleLine = true,
        isError = state.pairError,
        visualTransformation = PasswordVisualTransformation(),
        supportingText = {
            if (state.pairError) {
                Text(stringResource(R.string.pair_token_empty))
            }
        },
    )
    Button(onClick = onSave, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.action_save_token))
    }
}

@Composable
private fun WifiPairForm(
    state: HomeUiState,
    onWifiPayloadChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onSubmitWifi: () -> Unit,
    onScanQr: () -> Unit,
    onConfirmWifi: () -> Unit,
    onCancelWifi: () -> Unit,
) {
    val pending = state.pendingWifi
    if (pending != null) {
        WifiConfirmCard(pending = pending, state = state, onAliasChange = onAliasChange, onConfirmWifi = onConfirmWifi, onCancelWifi = onCancelWifi)
        return
    }
    Text(text = stringResource(R.string.pair_wifi_intro), style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.action_scan_qr))
    }
    OutlinedTextField(
        value = state.wifiPayloadInput,
        onValueChange = onWifiPayloadChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.pair_wifi_payload_label)) },
        minLines = 3,
        isError = state.pairError,
    )
    Button(onClick = onSubmitWifi, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.action_review_fingerprint))
    }
}

@Composable
private fun WifiConfirmCard(
    pending: PairingPayload,
    state: HomeUiState,
    onAliasChange: (String) -> Unit,
    onConfirmWifi: () -> Unit,
    onCancelWifi: () -> Unit,
) {
    Text(text = stringResource(R.string.pair_wifi_confirm_title), style = MaterialTheme.typography.titleMedium)
    Text(text = stringResource(R.string.pair_wifi_host, "${pending.host}:${pending.port}"))
    Text(text = stringResource(R.string.pair_wifi_fingerprint, pending.pin.removePrefix("sha256/").take(20)))
    OutlinedTextField(
        value = state.aliasInput,
        onValueChange = onAliasChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.pair_alias_label)) },
        singleLine = true,
    )
    Button(
        onClick = onConfirmWifi,
        enabled = !state.pairingBusy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(if (state.pairingBusy) stringResource(R.string.pair_wifi_pairing) else stringResource(R.string.action_confirm_pair))
    }
    TextButton(onClick = onCancelWifi, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(R.string.action_cancel))
    }
}
