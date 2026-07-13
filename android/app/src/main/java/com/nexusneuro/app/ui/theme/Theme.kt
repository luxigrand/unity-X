package com.nexusneuro.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val NexusBlack = Color(0xFF000000)
private val NexusWhite = Color(0xFFFFFFFF)
private val NexusGray = Color(0xFF0A0A0A)
private val NexusDim = Color(0xFF333333)

private val NexusColors = darkColorScheme(
    primary = NexusWhite,
    onPrimary = NexusBlack,
    secondary = NexusWhite,
    onSecondary = NexusBlack,
    background = NexusBlack,
    onBackground = NexusWhite,
    surface = NexusGray,
    onSurface = NexusWhite,
    outline = NexusWhite,
    error = NexusWhite,
)

val MonoStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    color = NexusWhite,
)

@Composable
fun NexusNeuroTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NexusColors,
        typography = MaterialTheme.typography.copy(
            bodyLarge = MonoStyle.copy(fontSize = 16.sp),
            bodyMedium = MonoStyle.copy(fontSize = 14.sp),
            bodySmall = MonoStyle.copy(fontSize = 12.sp),
            titleLarge = MonoStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
            titleMedium = MonoStyle.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            labelLarge = MonoStyle.copy(fontSize = 14.sp, letterSpacing = 1.sp),
        ),
        content = content,
    )
}

object NexusPalette {
    val Black = NexusBlack
    val White = NexusWhite
    val Gray = NexusGray
    val Dim = NexusDim
}
