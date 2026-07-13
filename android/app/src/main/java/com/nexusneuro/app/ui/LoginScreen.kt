package com.nexusneuro.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import com.nexusneuro.app.ui.theme.MonoStyle
import com.nexusneuro.app.ui.theme.NexusPalette

@Composable
fun LoginScreen(
    error: String?,
    onLogin: (nationalId: String, password: String) -> Unit,
) {
    var nationalId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .border(2.dp, NexusPalette.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "NEXUS NEURO",
                style = MonoStyle.copy(fontSize = 28.sp, letterSpacing = 4.sp),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Personel Girişi",
                style = MonoStyle.copy(fontSize = 14.sp, color = NexusPalette.White.copy(alpha = 0.8f)),
            )
            Spacer(Modifier.height(24.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NexusPalette.White,
                unfocusedBorderColor = NexusPalette.White,
                focusedTextColor = NexusPalette.White,
                unfocusedTextColor = NexusPalette.White,
                cursorColor = NexusPalette.White,
                focusedLabelColor = NexusPalette.White,
                unfocusedLabelColor = NexusPalette.White.copy(alpha = 0.7f),
            )

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
                border = androidx.compose.foundation.BorderStroke(1.dp, NexusPalette.White),
            ) {
                Text("GİRİŞ", style = MonoStyle.copy(letterSpacing = 2.sp))
            }

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White),
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Administrator → Manuel · Personel → Auto / AI Co-Pilot",
                style = MonoStyle.copy(fontSize = 11.sp, color = NexusPalette.White.copy(alpha = 0.6f)),
                textAlign = TextAlign.Center,
            )
        }
    }
}
