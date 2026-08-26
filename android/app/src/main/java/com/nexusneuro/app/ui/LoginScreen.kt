package com.nexusneuro.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusneuro.app.auth.UnlockChallenge
import com.nexusneuro.app.ui.theme.MonoStyle
import com.nexusneuro.app.ui.theme.NexusPalette

@Composable
fun LoginScreen(
    error: String?,
    challenge: UnlockChallenge,
    biometricPending: Boolean,
    savedDisplayName: String?,
    unlockCount: Int,
    onLogin: (nationalId: String, password: String) -> Unit,
    onRequestBiometric: () -> Unit,
    onSwitchAccount: () -> Unit,
) {
    var nationalId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val showCredentials = challenge == UnlockChallenge.FullCredentials && !biometricPending
    val showSwitch = savedDisplayName != null || challenge != UnlockChallenge.FullCredentials || biometricPending

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val widthClass = windowWidthClass(maxWidth)
        val formMax = when (widthClass) {
            WindowWidthClass.Compact -> 420.dp
            WindowWidthClass.Medium -> 480.dp
            WindowWidthClass.Expanded -> 520.dp
        }
        val outerPad = contentHorizontalPadding(widthClass)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(outerPad),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = formMax)
                    .fillMaxWidth()
                    .border(2.dp, NexusPalette.White)
                    .padding(if (widthClass.isTablet) 32.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "UNITY-X",
                    style = MonoStyle.copy(
                        fontSize = if (widthClass.isTablet) 34.sp else 28.sp,
                        letterSpacing = 4.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        biometricPending && challenge == UnlockChallenge.DualBiometric ->
                            "Çift Biyometrik Doğrulama"
                        biometricPending -> "Biyolojik Doğrulama"
                        challenge == UnlockChallenge.FullCredentials ->
                            if (savedDisplayName != null) "Periyodik Kimlik Doğrulama" else "Personel Girişi"
                        else -> "Hızlı Giriş"
                    },
                    style = MonoStyle.copy(fontSize = 14.sp, color = NexusPalette.White.copy(alpha = 0.8f)),
                )

                if (savedDisplayName != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Hesap: $savedDisplayName · Giriş #$unlockCount",
                        style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.65f)),
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(20.dp))

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NexusPalette.White,
                    unfocusedBorderColor = NexusPalette.White,
                    focusedTextColor = NexusPalette.White,
                    unfocusedTextColor = NexusPalette.White,
                    cursorColor = NexusPalette.White,
                    focusedLabelColor = NexusPalette.White,
                    unfocusedLabelColor = NexusPalette.White.copy(alpha = 0.7f),
                    disabledBorderColor = NexusPalette.White.copy(alpha = 0.3f),
                    disabledTextColor = NexusPalette.White.copy(alpha = 0.5f),
                    disabledLabelColor = NexusPalette.White.copy(alpha = 0.4f),
                )

                if (showCredentials || biometricPending) {
                    if (showCredentials) {
                        OutlinedTextField(
                            value = nationalId,
                            onValueChange = { if (it.length <= 11) nationalId = it.filter { c -> c.isDigit() } },
                            label = { Text("Kimlik Numarası", style = MonoStyle.copy(fontSize = 12.sp)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MonoStyle,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Şifre", style = MonoStyle.copy(fontSize = 12.sp)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MonoStyle,
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(
                            onClick = { onLogin(nationalId, password) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NexusPalette.Black,
                                contentColor = NexusPalette.White,
                            ),
                            border = BorderStroke(1.dp, NexusPalette.White),
                        ) {
                            Text("GİRİŞ", style = MonoStyle.copy(letterSpacing = 2.sp))
                        }
                    }
                }

                if (biometricPending) {
                    val hint = when (challenge) {
                        UnlockChallenge.DualBiometric ->
                            "Bu girişte önce parmak izi, sonra yüz ayrı ayrı doğrulanacak (her 10. giriş)."
                        UnlockChallenge.SingleBiometric ->
                            "Parmak izi veya yüz ile devam edin."
                        UnlockChallenge.FullCredentials ->
                            "Kimlik doğrulandı. Biyometrik doğrulama gerekli."
                    }
                    Text(
                        text = hint,
                        style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.85f)),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = onRequestBiometric,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexusPalette.Black,
                            contentColor = NexusPalette.White,
                        ),
                        border = BorderStroke(1.dp, NexusPalette.White),
                    ) {
                        Text(
                            when (challenge) {
                                UnlockChallenge.DualBiometric -> "PARMAK + YÜZ DOĞRULA"
                                else -> "BİYOMETRİK GİRİŞ"
                            },
                            style = MonoStyle.copy(letterSpacing = 1.sp, fontSize = 13.sp),
                        )
                    }
                }

                if (!showCredentials && !biometricPending && challenge != UnlockChallenge.FullCredentials) {
                    Text(
                        text = "Hazırlanıyor…",
                        style = MonoStyle.copy(fontSize = 12.sp),
                    )
                }

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White),
                        textAlign = TextAlign.Center,
                    )
                }

                if (showSwitch && (savedDisplayName != null || biometricPending || challenge != UnlockChallenge.FullCredentials)) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onSwitchAccount) {
                        Text(
                            "Başka hesap",
                            style = MonoStyle.copy(
                                fontSize = 13.sp,
                                letterSpacing = 1.sp,
                                color = NexusPalette.White,
                            ),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Her 10 giriş: parmak+yüz · Her 20 giriş: kimlik+şifre",
                    style = MonoStyle.copy(fontSize = 10.sp, color = NexusPalette.White.copy(alpha = 0.5f)),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
