package com.codex.remote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codex.remote.ui.CodexRemoteApp
import com.codex.remote.ui.theme.CodexRemoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CodexRemoteTheme {
                val appViewModel: AppViewModel = viewModel()
                CodexRemoteApp(appViewModel)
            }
        }
    }
}
