package com.nexusneuro.app.ui



import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import com.nexusneuro.app.domain.Config
import com.nexusneuro.app.domain.ControlMode
import com.nexusneuro.app.domain.SleepStage
import com.nexusneuro.app.ui.theme.MonoStyle
import com.nexusneuro.app.ui.theme.NexusPalette



@Composable
fun DashboardScreen(vm: SessionViewModel) {
    val user = vm.user ?: return



    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(NexusPalette.Black),
    ) {
        val widthClass = windowWidthClass(maxWidth)
        val pad = contentHorizontalPadding(widthClass)
        val chartHeight = when (widthClass) {
            WindowWidthClass.Compact -> 180.dp
            WindowWidthClass.Medium -> 220.dp
            WindowWidthClass.Expanded -> 260.dp
        }



        when (widthClass) {
            WindowWidthClass.Compact -> PhoneDashboard(vm, user.displayName, user.role.label, pad, chartHeight)
            WindowWidthClass.Medium,
            WindowWidthClass.Expanded,
            -> TabletDashboard(vm, user.displayName, user.role.label, pad, chartHeight, widthClass)
        }
    }
}



@Composable
private fun PhoneDashboard(
    vm: SessionViewModel,
    displayName: String,
    roleLabel: String,
    pad: androidx.compose.ui.unit.Dp,
    chartHeight: androidx.compose.ui.unit.Dp,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(pad),
    ) {
        HeaderBar(displayName, roleLabel) { vm.logout() }
        Spacer(Modifier.height(12.dp))
        ModeSelector(vm)
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = NexusPalette.White.copy(alpha = 0.3f))
        Spacer(Modifier.height(12.dp))
        StatusBanner(vm)
        Spacer(Modifier.height(12.dp))
        MetricsRow(vm)
        Spacer(Modifier.height(8.dp))
        StimLines(vm)
        Spacer(Modifier.height(12.dp))
        WaveformChart("EEG (µV)", vm.eegPoints, chartHeight = chartHeight, emptyLabel = "EEG bekleniyor…")
        Spacer(Modifier.height(10.dp))
        WaveformChart("Nabız Dalga", vm.pulsePoints, chartHeight = chartHeight, emptyLabel = "Nabız bekleniyor…")
        Spacer(Modifier.height(14.dp))
        ControlsSection(vm)
        Spacer(Modifier.height(14.dp))
        SessionButton(vm)
        CopilotBox(vm)
        FooterMeta()
    }
}



@Composable
private fun TabletDashboard(
    vm: SessionViewModel,
    displayName: String,
    roleLabel: String,
    pad: androidx.compose.ui.unit.Dp,
    chartHeight: androidx.compose.ui.unit.Dp,
    widthClass: WindowWidthClass,
) {
    val leftScroll = rememberScrollState()
    val rightScroll = rememberScrollState()
    val sideMax = if (widthClass == WindowWidthClass.Expanded) 420.dp else 360.dp



    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(pad),
    ) {
        HeaderBar(displayName, roleLabel) { vm.logout() }
        Spacer(Modifier.height(16.dp))



        Row(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Main: status + charts
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(leftScroll),
            ) {
                StatusBanner(vm)
                Spacer(Modifier.height(12.dp))
                MetricsRow(vm)
                Spacer(Modifier.height(8.dp))
                StimLines(vm)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    WaveformChart(
                        title = "EEG (µV)",
                        points = vm.eegPoints,
                        modifier = Modifier.weight(1f),
                        chartHeight = chartHeight,
                        emptyLabel = "EEG bekleniyor…",
                    )
                    WaveformChart(
                        title = "Nabız Dalga",
                        points = vm.pulsePoints,
                        modifier = Modifier.weight(1f),
                        chartHeight = chartHeight,
                        emptyLabel = "Nabız bekleniyor…",
                    )
                }
                Spacer(Modifier.height(12.dp))
                CopilotBox(vm)
                FooterMeta()
            }



            // Side panel: modes + controls
            Column(
                modifier = Modifier
                    .widthIn(max = sideMax)
                    .fillMaxWidth(fraction = if (widthClass == WindowWidthClass.Expanded) 0.34f else 0.40f)
                    .fillMaxHeight()
                    .border(1.dp, NexusPalette.White.copy(alpha = 0.5f))
                    .padding(16.dp)
                    .verticalScroll(rightScroll),
            ) {
                Text("Kontrol Paneli", style = MonoStyle.copy(fontSize = 14.sp, letterSpacing = 1.sp))
                Spacer(Modifier.height(12.dp))
                ModeSelector(vm)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = NexusPalette.White.copy(alpha = 0.3f))
                Spacer(Modifier.height(16.dp))
                ControlsSection(vm)
                Spacer(Modifier.height(16.dp))
                SessionButton(vm)
            }
        }
    }
}



@Composable
private fun HeaderBar(displayName: String, roleLabel: String, onLogout: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("UNITY-X", style = MonoStyle.copy(fontSize = 20.sp, letterSpacing = 2.sp))
            Text(
                "$displayName · $roleLabel",
                style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.7f)),
            )
        }
        OutlineButton("Çıkış", onClick = onLogout)
    }
}



@Composable
private fun ModeSelector(vm: SessionViewModel) {
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
}



@Composable
private fun StatusBanner(vm: SessionViewModel) {
    Text(
        vm.statusLabel(),
        style = MonoStyle.copy(fontSize = 18.sp, letterSpacing = 1.sp),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, NexusPalette.White)
            .padding(12.dp),
    )
}



@Composable
private fun MetricsRow(vm: SessionViewModel) {
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
            Text(
                if (vm.sessionActive) "Kaynak: ${vm.pulseSource}" else "Kaynak: —",
                style = MonoStyle.copy(fontSize = 10.sp, color = NexusPalette.White.copy(alpha = 0.6f)),
            )
            vm.currentSpo2?.let { spo2 ->
                Text(
                    "SpO₂ ${spo2.toInt()}%",
                    style = MonoStyle.copy(fontSize = 11.sp, color = NexusPalette.White.copy(alpha = 0.75f)),
                )
            }
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
}



@Composable
private fun StimLines(vm: SessionViewModel) {
    Text(
        vm.stimStatus,
        style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.85f)),
    )
    Text(
        "Mod: ${vm.controlMode.label}",
        style = MonoStyle.copy(fontSize = 12.sp, color = NexusPalette.White.copy(alpha = 0.7f)),
    )
}



@Composable
private fun ControlsSection(vm: SessionViewModel) {
    if (vm.showManualPanel()) {
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



    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Kişiye Ses (TTS)", style = MonoStyle.copy(fontSize = 13.sp))
        Switch(
            checked = vm.deviceVoiceEnabled,
            onCheckedChange = { vm.toggleDeviceVoice(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = NexusPalette.Black,
                checkedTrackColor = NexusPalette.White,
                uncheckedThumbColor = NexusPalette.White,
                uncheckedTrackColor = NexusPalette.Dim,
            ),
        )
    }
    Spacer(Modifier.height(6.dp))
    OutlineButton("Ses Testi", Modifier.fillMaxWidth()) { vm.testVoice() }
}



@Composable
private fun SessionButton(vm: SessionViewModel) {
    OutlineButton(
        if (vm.sessionActive) "Stop Session" else "Start Session",
        Modifier.fillMaxWidth(),
    ) {
        if (vm.sessionActive) vm.stopSession() else vm.startSession()
    }
}



@Composable
private fun CopilotBox(vm: SessionViewModel) {
    if (!vm.copilotEnabled || vm.copilotMessages.isEmpty()) return
    Spacer(Modifier.height(14.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, NexusPalette.White)
            .padding(12.dp),
    ) {
        Text(
            when (vm.controlMode) {
                ControlMode.COPILOT -> "AI CO-PILOT"
                else -> "AI / LUCID"
            },
            style = MonoStyle.copy(fontSize = 13.sp, letterSpacing = 1.sp),
        )
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



@Composable
private fun FooterMeta() {
    Spacer(Modifier.height(16.dp))
    Text(
        "EEG: ${Config.SAMPLE_RATE} Hz · Pencere: ${Config.WINDOW_SECONDS}s",
        style = MonoStyle.copy(fontSize = 11.sp, color = NexusPalette.White.copy(alpha = 0.5f)),
    )
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
