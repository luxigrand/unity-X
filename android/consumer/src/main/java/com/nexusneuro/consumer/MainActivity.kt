package com.nexusneuro.consumer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexusneuro.consumer.data.SupabaseProvider
import com.nexusneuro.consumer.ui.ConsumerApp
import com.nexusneuro.consumer.ui.ConsumerViewModel
import io.github.jan.supabase.auth.handleDeeplinks
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthDeeplink(intent)
        enableEdgeToEdge()
        setContent {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .safeDrawingPadding(),
                color = Color.Black,
            ) {
                val vm: ConsumerViewModel = viewModel()
                ConsumerApp(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthDeeplink(intent)
    }

    private fun handleAuthDeeplink(intent: Intent?) {
        if (intent == null) return
        lifecycleScope.launch {
            try {
                SupabaseProvider.client.handleDeeplinks(intent)
            } catch (_: Exception) {
                // Ignore non-auth intents
            }
        }
    }
}
