package com.nexusneuro.app.domain

object Config {
    const val SAMPLE_RATE: Int = 256
    const val CHUNK_SIZE: Int = 64
    const val WINDOW_SECONDS: Float = 4.0f
    const val CHART_SECONDS: Float = 10.0f

    const val TRIGGER_COMMAND: String = "TRIGGER_40HZ\n"
    const val STOP_COMMAND: String = "STOP_STIM\n"
    const val WAKE_COMMAND: String = "WAKE_UP\n"
    const val TRIGGER_COOLDOWN_SEC: Float = 30.0f

    val STAGE_DURATION_RANGES: Map<String, Pair<Float, Float>> = mapOf(
        "awake" to (15.0f to 45.0f),
        "deep_sleep" to (30.0f to 90.0f),
        "rem" to (20.0f to 60.0f),
    )

    val FREQUENCY_BANDS: Map<String, Pair<Float, Float>> = mapOf(
        "delta" to (0.5f to 4.0f),
        "theta" to (4.0f to 8.0f),
        "alpha" to (8.0f to 13.0f),
        "beta" to (13.0f to 30.0f),
    )

    const val DELTA_DOMINANCE_THRESHOLD: Float = 0.55f
    const val THETA_REM_THRESHOLD: Float = 0.25f
    const val EOG_SPIKE_STD_MULTIPLIER: Float = 3.0f

    val PULSE_RANGES: Map<String, Pair<Int, Int>> = mapOf(
        "awake" to (68 to 88),
        "deep_sleep" to (48 to 62),
        "rem" to (62 to 92),
    )

    const val TICK_MS: Long = 250L
}
