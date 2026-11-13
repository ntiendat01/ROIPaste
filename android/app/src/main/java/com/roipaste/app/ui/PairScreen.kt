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
import com.roipaste.app.presentation.HomeUiState

@Composable
fun PairScreen(
    state: HomeUiState,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
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
            Text(text = stringResource(R.string.pair_method_usb), style = MaterialTheme.typography.titleMedium)
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
            Text(text = stringResource(R.string.pair_method_wifi), style = MaterialTheme.typography.titleMedium)
            Text(text = stringResource(R.string.pair_wifi_disabled), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_back))
            }
        }
    }
}
