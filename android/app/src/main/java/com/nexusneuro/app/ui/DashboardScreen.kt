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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexusneuro.app.domain.SleepStage
import com.nexusneuro.app.ui.theme.MonoStyle
import com.nexusneuro.app.ui.theme.NexusPalette

@Composable
fun DashboardScreen(vm: SessionViewModel) {
    val user = vm.user ?: return
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusPalette.Black)
            .verticalScroll(scroll)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("NEXUS NEURO", style = MonoStyle.copy(fontSize = 20.sp, letterSpacing = 2.sp))
                Text(
                    "${user.displayName} · ${user.role.label}",
                    style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.7f)),
                )
            }
            OutlineButton("Çıkış") { vm.logout() }
        }

        Spacer(Modifier.height(12.dp))
        Text("Kontrol Modu", style = MonoStyle.copy(fontSize = 13.sp, letterSpacing = 1.sp))
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.allowedModes().forEach { mode ->
                FilterChip(
                    selected = vm.controlMode == mode,
                    onClick = { vm.setMode(mode) },
                    label = { Text(mode.label, style = MonoStyle.copy(fontSize = 12.sp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NexusPalette.White,
                        selectedLabelColor = NexusPalette.Black,
                        containerColor = NexusPalette.Black,
                        labelColor = NexusPalette.White,
                    ),
                    border = BorderStroke(1.dp, NexusPalette.White),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = NexusPalette.White.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))

        Text(
            vm.statusLabel(),
            style = MonoStyle.copy(fontSize = 18.sp, letterSpacing = 1.sp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, NexusPalette.White)
                .padding(12.dp),
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(2.dp, NexusPalette.White)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("NABIZ", style = MonoStyle.copy(fontSize = 11.sp, letterSpacing = 2.sp))
                Text(
                    if (vm.sessionActive) "${vm.currentBpm.toInt()} BPM" else "— BPM",
                    style = MonoStyle.copy(fontSize = 28.sp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(2.dp, NexusPalette.White)
                    .padding(12.dp),
            ) {
                Text("AŞAMA", style = MonoStyle.copy(fontSize = 11.sp, letterSpacing = 2.sp))
                Text(
                    if (vm.sessionActive) vm.displayStage.label else "Idle",
                    style = MonoStyle.copy(fontSize = 16.sp),
                )
                Text(
                    "Güven: ${"%.0f".format(vm.confidence * 100)}%",
                    style = MonoStyle.copy(fontSize = 11.sp, color = NexusPalette.White.copy(alpha = 0.6f)),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            vm.stimStatus,
            style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.85f)),
        )
        Text(
            "Mod: ${vm.controlMode.label}",
            style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.7f)),
        )

        Spacer(Modifier.height(12.dp))
        WaveformChart(
            title = "EEG (µV)",
            points = vm.eegPoints,
            emptyLabel = "EEG bekleniyor…",
        )
        Spacer(Modifier.height(10.dp))
        WaveformChart(
            title = "Nabız Dalga",
            points = vm.pulsePoints,
            emptyLabel = "Nabız bekleniyor…",
        )

        Spacer(Modifier.height(14.dp))
        if (vm.canManual()) {
            Text("Manuel Kontroller", style = MonoStyle.copy(fontSize = 13.sp, letterSpacing = 1.sp))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SleepStage.entries.forEach { stage ->
                    FilterChip(
                        selected = vm.manualStage == stage,
                        onClick = { vm.manualStage = stage },
                        label = { Text(stage.label, style = MonoStyle.copy(fontSize = 11.sp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexusPalette.White,
                            selectedLabelColor = NexusPalette.Black,
                            containerColor = NexusPalette.Black,
                            labelColor = NexusPalette.White,
                        ),
                        border = BorderStroke(1.dp, NexusPalette.White),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlineButton("40Hz Tetikle", Modifier.weight(1f)) { vm.trigger40Hz() }
                OutlineButton("Durdur", Modifier.weight(1f)) { vm.stopStim() }
            }
            Spacer(Modifier.height(8.dp))
            OutlineButton("UYANDIR", Modifier.fillMaxWidth()) { vm.wakeUp() }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Otomatik REM Tetikleme", style = MonoStyle.copy(fontSize = 13.sp))
                Switch(
                    checked = vm.autoTriggerEnabled,
                    onCheckedChange = { vm.autoTriggerEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NexusPalette.Black,
                        checkedTrackColor = NexusPalette.White,
                        uncheckedThumbColor = NexusPalette.White,
                        uncheckedTrackColor = NexusPalette.Dim,
                    ),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlineButton(
                if (vm.sessionActive) "Stop Session" else "Start Session",
                Modifier.weight(1f),
            ) {
                if (vm.sessionActive) vm.stopSession() else vm.startSession()
            }
        }

        if (vm.copilotEnabled && vm.copilotMessages.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, NexusPalette.White)
                    .padding(12.dp),
            ) {
                Text("AI CO-PILOT", style = MonoStyle.copy(fontSize = 13.sp, letterSpacing = 1.sp))
                Spacer(Modifier.height(6.dp))
                vm.copilotMessages.forEach { msg ->
                    Text(
                        "[${msg.priority.uppercase()}] ${msg.text}",
                        style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.9f)),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "EEG: ${com.nexusneuro.app.domain.Config.SAMPLE_RATE} Hz · Pencere: ${com.nexusneuro.app.domain.Config.WINDOW_SECONDS}s",
            style = MonoStyle.copy(fontSize = 11.sp, color = NexusPalette.White.copy(alpha = 0.5f)),
        )
    }
}

@Composable
private fun OutlineButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = NexusPalette.Black,
            contentColor = NexusPalette.White,
        ),
        border = BorderStroke(1.dp, NexusPalette.White),
    ) {
        Text(label.uppercase(), style = MonoStyle.copy(fontSize = 12.sp, letterSpacing = 1.sp))
    }
}
