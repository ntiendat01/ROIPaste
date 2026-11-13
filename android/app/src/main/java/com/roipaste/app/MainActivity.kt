package com.roipaste.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roipaste.app.presentation.HomeViewModel
import com.roipaste.app.ui.AppNavHost
import com.roipaste.app.ui.theme.RoiPasteTheme

class MainActivity : ComponentActivity() {
    private val app: RoiPasteApp
        get() = application as RoiPasteApp

    private val viewModel: HomeViewModel by viewModels { app.container.homeFactory() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val versionName = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            RoiPasteTheme {
                AppNavHost(
                    state = state,
                    versionName = versionName,
                    onSend = viewModel::send,
                    onFetch = viewModel::fetch,
                    onCopy = viewModel::copyToAndroid,
                    onTogglePreview = viewModel::togglePreview,
                    onReconnect = viewModel::reconnect,
                    onTogglePause = viewModel::setSyncPaused,
                    onOpenPair = viewModel::openPair,
                    onOpenSettings = viewModel::openSettings,
                    onTokenChange = viewModel::updateTokenInput,
                    onSaveToken = viewModel::saveToken,
                    onUnpair = viewModel::unpair,
                    onBack = viewModel::closeOverlay,
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.container.mainFocus.resumed = true
    }

    override fun onPause() {
        app.container.mainFocus.resumed = false
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        app.container.mainFocus.windowFocused = hasFocus
    }
}
