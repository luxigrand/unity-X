package com.nexusneuro.app.wear

/**
 * Shared message path / JSON keys for watch ↔ phone vitals.
 */
object VitalsProtocol {
    const val PATH = "/nexus/vitals"
    const val KEY_BPM = "bpm"
    const val KEY_SPO2 = "spo2"
    const val KEY_TS = "ts"
    const val KEY_AVAILABILITY = "availability"
    const val KEY_SOURCE = "source"
}
