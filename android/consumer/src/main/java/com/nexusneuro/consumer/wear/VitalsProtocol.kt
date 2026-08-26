package com.nexusneuro.consumer.wear

/**
 * Watch ↔ phone vitals path (must match :wear).
 */
object VitalsProtocol {
    const val PATH = "/nexus/vitals"
    const val KEY_BPM = "bpm"
    const val KEY_SPO2 = "spo2"
    const val KEY_TS = "ts"
    const val KEY_AVAILABILITY = "availability"
    const val FRESH_MS = 5_000L
}

data class WatchVitals(
    val bpm: Float? = null,
    val spo2: Float? = null,
    val timestampMs: Long = 0L,
    val availability: String = "UNKNOWN",
) {
    fun isFresh(nowMs: Long = System.currentTimeMillis()): Boolean =
        timestampMs > 0L && (nowMs - timestampMs) <= VitalsProtocol.FRESH_MS
}
