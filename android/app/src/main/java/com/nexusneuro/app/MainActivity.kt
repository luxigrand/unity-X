package com.nexusneuro.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusneuro.app.ui.DashboardScreen
import com.nexusneuro.app.ui.LoginScreen
import com.nexusneuro.app.ui.SessionViewModel
import com.nexusneuro.app.ui.theme.NexusNeuroTheme
import com.nexusneuro.app.ui.theme.NexusPalette

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NexusNeuroTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(NexusPalette.Black)
                        .safeDrawingPadding(),
                    color = NexusPalette.Black,
                ) {
                    NexusNeuroApp()
                }
            }
        }
    }
}

@Composable
fun NexusNeuroApp(vm: SessionViewModel = viewModel()) {
    if (vm.user == null) {
        LoginScreen(
            error = vm.loginError,
            onLogin = { id, pw -> vm.login(id, pw) },
        )
    } else {
        DashboardScreen(vm)
    }
}
