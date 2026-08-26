package com.nexusneuro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusneuro.app.ui.theme.MonoStyle
import com.nexusneuro.app.ui.theme.NexusPalette

@Composable
fun UserHomeScreen(vm: SessionViewModel) {
    val user = vm.user ?: return
    val connected = vm.watchConnected
    val statusColor = if (connected) Color(0xFF8BC34A) else Color(0xFFFF9800)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusPalette.Black)
            .padding(24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "UNITY-X",
                    style = MonoStyle.copy(fontSize = 18.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    user.displayName,
                    style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.65f)),
                )
            }
            Button(
                onClick = { vm.logout() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NexusPalette.Black,
                    contentColor = NexusPalette.White,
                ),
                border = BorderStroke(1.dp, NexusPalette.White),
            ) {
                Text("ÇIKIŞ", style = MonoStyle.copy(fontSize = 12.sp))
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NexusPalette.White.copy(alpha = 0.5f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(statusColor),
            )
            Column {
                Text(
                    if (connected) "Saat bağlı" else "Saat bağlı değil",
                    style = MonoStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    if (connected) "Veri saatten geliyor" else "Saatte uygulamayı açıp BAŞLAT’a basın",
                    style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.65f)),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "NABIZ",
                style = MonoStyle.copy(
                    fontSize = 14.sp,
                    letterSpacing = 3.sp,
                    color = NexusPalette.White.copy(alpha = 0.7f),
                ),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                vm.liveBpm?.toInt()?.toString() ?: "—",
                style = MonoStyle.copy(fontSize = 72.sp, fontWeight = FontWeight.Bold),
                textAlign = TextAlign.Center,
            )
            Text(
                "BPM",
                style = MonoStyle.copy(fontSize = 18.sp, color = NexusPalette.White.copy(alpha = 0.75f)),
            )

            vm.liveSpo2?.let { spo2 ->
                Spacer(Modifier.height(20.dp))
                Text(
                    "SpO₂  ${spo2.toInt()}%",
                    style = MonoStyle.copy(fontSize = 22.sp),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            vm.userVitalsStatus,
            style = MonoStyle.copy(
                fontSize = 13.sp,
                color = NexusPalette.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Saatte unity-X → BAŞLAT",
            style = MonoStyle.copy(
                fontSize = 12.sp,
                color = NexusPalette.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}
