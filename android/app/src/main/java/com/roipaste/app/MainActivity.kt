package com.roipaste.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.roipaste.app.ui.HomeScreen
import com.roipaste.app.ui.theme.RoiPasteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RoiPasteTheme {
                HomeScreen(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
