package com.nexusneuro.app.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import kotlinx.coroutines.launch

class WearMainActivity : ComponentActivity() {
    private lateinit var healthReader: HealthReader
    private lateinit var sender: WatchTelemetrySender

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        if (result.values.any { it }) {
            lifecycleScope.launch {
                healthReader.discoverCapabilities()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sender = WatchTelemetrySender(this)
        healthReader = HealthReader(this, lifecycleScope, sender)

        setContent {
            MaterialTheme {
                val ui by healthReader.ui.collectAsStateWithLifecycle()
                WearVitalsScreen(
                    ui = ui,
                    onToggle = {
                        if (ui.measuring) healthReader.stop() else healthReader.start()
                    },
                )
            }
        }

        ensurePermissionsThenDiscover()
    }

    override fun onDestroy() {
        if (::healthReader.isInitialized) {
            healthReader.stop()
        }
        super.onDestroy()
    }

    private fun ensurePermissionsThenDiscover() {
        val needed = listOf(Manifest.permission.BODY_SENSORS).filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isEmpty()) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    healthReader.discoverCapabilities()
                }
            }
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }
}

@Composable
private fun WearVitalsScreen(
    ui: WearVitalsUi,
    onToggle: () -> Unit,
) {
    val listState = rememberScalingLazyListState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    text = "UNITY-X",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                )
            }
            item {
                Text(
                    text = ui.bpm?.let { "${it.toInt()}" } ?: "—",
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = "BPM",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                )
            }
            item {
                Text(
                    text = when {
                        ui.supportsSpo2 && ui.spo2 != null -> "SpO₂ ${ui.spo2.toInt()}%"
                        ui.supportsSpo2 -> "SpO₂ bekleniyor"
                        else -> "SpO₂ bu saatte yok"
                    },
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Text(
                    text = ui.status,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                )
            }
            item {
                Text(
                    text = if (ui.phoneConnected) "Telefon bağlı" else "Telefon yok",
                    color = if (ui.phoneConnected) Color(0xFF8BC34A) else Color(0xFFFF9800),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ui.error?.let { err ->
                item {
                    Text(
                        text = err,
                        color = Color(0xFFFF5252),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Button(
                    onClick = onToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    enabled = ui.supportsHeartRate || ui.measuring,
                    colors = ButtonDefaults.primaryButtonColors(
                        backgroundColor = Color.White,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(
                        text = if (ui.measuring) "DURDUR" else "BAŞLAT",
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (!ui.supportsHeartRate && ui.error == null) {
                item {
                    Text(
                        text = "Nabız sensörü hazır değil / izin ver",
                        color = Color(0xFFFF9800),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
