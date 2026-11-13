package com.roipaste.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.roipaste.app.presentation.HomeScreenKind
import com.roipaste.app.presentation.HomeUiState

@Composable
fun AppNavHost(
    state: HomeUiState,
    versionName: String,
    onSend: () -> Unit,
    onFetch: () -> Unit,
    onCopy: () -> Unit,
    onTogglePreview: () -> Unit,
    onReconnect: () -> Unit,
    onTogglePause: (Boolean) -> Unit,
    onOpenPair: () -> Unit,
    onOpenSettings: () -> Unit,
    onTokenChange: (String) -> Unit,
    onSaveToken: () -> Unit,
    onUnpair: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSelectMethod: (com.roipaste.app.presentation.PairMethod) -> Unit,
    onWifiPayloadChange: (String) -> Unit,
    onAliasChange: (String) -> Unit,
    onSubmitWifi: () -> Unit,
    onScanQr: () -> Unit,
    onConfirmWifi: () -> Unit,
    onCancelWifi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.screen) {
        HomeScreenKind.Pair -> PairScreen(
            state = state,
            onTokenChange = onTokenChange,
            onSave = onSaveToken,
            onBack = onBack,
            onSelectMethod = onSelectMethod,
            onWifiPayloadChange = onWifiPayloadChange,
            onAliasChange = onAliasChange,
            onSubmitWifi = onSubmitWifi,
            onScanQr = onScanQr,
            onConfirmWifi = onConfirmWifi,
            onCancelWifi = onCancelWifi,
            modifier = modifier,
        )
        HomeScreenKind.Settings -> SettingsScreen(
            versionName = versionName,
            hasToken = state.hasToken,
            onUnpair = onUnpair,
            onBack = onBack,
            modifier = modifier,
        )
        HomeScreenKind.Home -> HomeScreen(
            state = state,
            onSend = onSend,
            onFetch = onFetch,
            onCopy = onCopy,
            onTogglePreview = onTogglePreview,
            onReconnect = onReconnect,
            onTogglePause = onTogglePause,
            onOpenPair = onOpenPair,
            onOpenSettings = onOpenSettings,
            onRetry = onRetry,
            modifier = modifier,
        )
    }
}
