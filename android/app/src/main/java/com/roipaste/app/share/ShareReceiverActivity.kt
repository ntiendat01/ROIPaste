package com.roipaste.app.share

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roipaste.app.RoiPasteApp
import com.roipaste.app.domain.SharePayload
import com.roipaste.app.presentation.ShareViewModel
import com.roipaste.app.ui.ShareConfirmScreen
import com.roipaste.app.ui.theme.RoiPasteTheme

class ShareReceiverActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val validation = SharePayload.parse(
            action = intent?.action,
            mimeType = intent?.type,
            text = intent?.getStringExtra(Intent.EXTRA_TEXT),
        )
        val factory = (application as RoiPasteApp).container.shareFactory(validation)
        val viewModel = ViewModelProvider(this, factory)[ShareViewModel::class.java]
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            RoiPasteTheme {
                ShareConfirmScreen(
                    state = state,
                    onTogglePreview = viewModel::togglePreview,
                    onConfirm = viewModel::confirmSend,
                    onCancel = { finish() },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
