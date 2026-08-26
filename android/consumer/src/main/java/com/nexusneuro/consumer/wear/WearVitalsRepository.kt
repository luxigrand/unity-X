package com.nexusneuro.consumer.wear

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

object WearVitalsRepository {
    private const val TAG = "ConsumerWearRx"

    private val _vitals = MutableStateFlow(WatchVitals())
    val vitals: StateFlow<WatchVitals> = _vitals.asStateFlow()

    @Volatile
    var acceptWearMessages: Boolean = false

    fun ingestMessage(path: String, data: ByteArray) {
        if (!acceptWearMessages) return
        if (path != VitalsProtocol.PATH) return
        try {
            val json = JSONObject(String(data, Charsets.UTF_8))
            val bpm = if (json.has(VitalsProtocol.KEY_BPM) && !json.isNull(VitalsProtocol.KEY_BPM)) {
                json.getDouble(VitalsProtocol.KEY_BPM).toFloat()
            } else {
                null
            }
            val spo2 = if (json.has(VitalsProtocol.KEY_SPO2) && !json.isNull(VitalsProtocol.KEY_SPO2)) {
                json.getDouble(VitalsProtocol.KEY_SPO2).toFloat()
            } else {
                null
            }
            _vitals.value = WatchVitals(
                bpm = bpm,
                spo2 = spo2,
                timestampMs = json.optLong(VitalsProtocol.KEY_TS, System.currentTimeMillis()),
                availability = json.optString(VitalsProtocol.KEY_AVAILABILITY, "UNKNOWN"),
            )
        } catch (e: Exception) {
            Log.w(TAG, "bad payload", e)
        }
    }
}
