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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusneuro.app.ui.theme.MonoStyle
import com.nexusneuro.app.ui.theme.NexusPalette

/**
 * Personel / Sunum: klinik panel değil; son kullanıcı için :consumer APK’sını kullanın.
 * Admin tam dashboard’da kalır.
 */
@Composable
fun PersonnelHubScreen(vm: SessionViewModel) {
    val user = vm.user ?: return
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
                Text("UNITY-X", style = MonoStyle.copy(fontSize = 18.sp, letterSpacing = 2.sp))
                Text(
                    "${user.displayName} · ${user.role.label}",
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

        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NexusPalette.White.copy(alpha = 0.5f))
                .padding(20.dp),
        ) {
            Text("Personel paneli", style = MonoStyle.copy(fontSize = 16.sp, letterSpacing = 1.sp))
            Spacer(Modifier.height(12.dp))
            Text(
                "Son kullanıcı nabız / saat deneyimi ayrı uygulamada:",
                style = MonoStyle.copy(fontSize = 13.sp, color = NexusPalette.White.copy(alpha = 0.8f)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "unity-X (consumer) — e-posta hesabı + Wear OS",
                style = MonoStyle.copy(fontSize = 13.sp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Klinik EEG / REM kontrolü için Administrator hesabıyla giriş yapın.",
                style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.55f)),
            )
        }
    }
}
