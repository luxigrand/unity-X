package com.nexusneuro.app.domain

enum class SleepStage(val label: String, val configKey: String) {
    AWAKE("Awake", "awake"),
    DEEP_SLEEP("Deep Sleep", "deep_sleep"),
    REM("REM Sleep", "rem");

    val displayLabel: String
        get() = if (this == REM) "REM Detected — Triggering 40Hz!" else label
}

enum class ControlMode(val label: String) {
    AUTO("Auto"),
    MANUAL("Manual"),
    COPILOT("AI Co-Pilot"),
}

data class EegChunk(
    val samples: FloatArray,
    val timestamp: Float,
    val trueStage: SleepStage,
)

data class PulseReading(
    val bpm: Float,
    val timestamp: Float,
    val waveformSample: Float,
)

data class DetectionResult(
    val detectedStage: SleepStage,
    val confidence: Float,
    val isRem: Boolean,
)

data class CopilotMessage(
    val text: String,
    val priority: String = "info",
    val timestamp: Float = 0f,
)

data class ChartPoint(
    val time: Float,
    val value: Float,
)
