package com.nexusneuro.consumer.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)
private val Green = Color(0xFF8BC34A)
private val Orange = Color(0xFFFF9800)

@Composable
fun ConsumerApp(vm: ConsumerViewModel) {
    if (!vm.loggedIn) {
        AuthScreen(vm)
    } else {
        HomeScreen(vm)
    }
}

@Composable
private fun AuthScreen(vm: ConsumerViewModel) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
            .verticalScroll(scroll)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "UNITY-X",
            color = White,
            fontSize = 22.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (vm.isRegisterMode) "Hesap oluştur" else "Giriş yap",
            color = White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = vm.emailInput,
            onValueChange = { vm.emailInput = it },
            label = { Text("E-posta") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = fieldColors(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = vm.passwordInput,
            onValueChange = { vm.passwordInput = it },
            label = { Text("Şifre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = fieldColors(),
        )
        vm.authError?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = Color(0xFFFF5252),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 4,
            )
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { vm.submitAuth() },
            enabled = !vm.busy && vm.emailInput.isNotBlank() && vm.passwordInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black),
        ) {
            Text(
                when {
                    vm.busy -> "…"
                    vm.isRegisterMode -> "KAYIT OL"
                    else -> "GİRİŞ"
                },
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
        TextButton(onClick = { vm.isRegisterMode = !vm.isRegisterMode }) {
            Text(
                if (vm.isRegisterMode) "Zaten hesabım var" else "Hesap oluştur",
                color = White.copy(alpha = 0.75f),
                fontFamily = FontFamily.Monospace,
            )
        }
        if (!vm.isRegisterMode) {
            TextButton(onClick = { vm.sendPasswordReset() }, enabled = !vm.busy) {
                Text(
                    "Şifremi unuttum",
                    color = White.copy(alpha = 0.75f),
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(vm: ConsumerViewModel) {
    val connectedColor = when {
        vm.connectionLabel.contains("Saat bağlı", ignoreCase = true) -> Green
        vm.connectionLabel.contains("Bulut", ignoreCase = true) -> Green
        else -> Orange
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Black)
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
                    color = White,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    vm.userEmail.orEmpty(),
                    color = White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Button(
                onClick = { vm.logout() },
                colors = ButtonDefaults.buttonColors(containerColor = Black, contentColor = White),
                border = BorderStroke(1.dp, White),
            ) {
                Text("ÇIKIŞ", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, White.copy(alpha = 0.45f))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(connectedColor),
            )
            Column {
                Text(
                    vm.connectionLabel,
                    color = White,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (vm.isPrimary) "Ana cihaz" else "İkincil cihaz",
                    color = White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
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
                color = White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                letterSpacing = 3.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                vm.displayBpm?.toInt()?.toString() ?: "—",
                color = White,
                fontSize = 72.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Text("BPM", color = White.copy(alpha = 0.75f), fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            vm.displaySpo2?.let { spo2 ->
                Spacer(Modifier.height(16.dp))
                Text(
                    "SpO₂  ${spo2.toInt()}%",
                    color = White,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            vm.statusLabel,
            color = White.copy(alpha = 0.7f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        vm.primaryHint?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                it,
                color = White.copy(alpha = 0.45f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        vm.authError?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Color(0xFFFF5252), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }

        if (!vm.isPrimary) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.claimPrimary() },
                enabled = !vm.busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Black),
            ) {
                Text("BU CİHAZI ANA YAP", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        } else {
            Spacer(Modifier.height(12.dp))
            Text(
                "Saatte unity-X → BAŞLAT",
                color = White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = White,
    unfocusedTextColor = White,
    focusedBorderColor = White,
    unfocusedBorderColor = White.copy(alpha = 0.5f),
    focusedLabelColor = White,
    unfocusedLabelColor = White.copy(alpha = 0.6f),
    cursorColor = White,
)
