package com.nexusneuro.app.wear

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Holds latest vitals from the paired Wear OS app.
 */
object WearVitalsRepository {
    private const val TAG = "NexusWearRx"

    private val _vitals = MutableStateFlow(WatchVitals())
    val vitals: StateFlow<WatchVitals> = _vitals.asStateFlow()

    fun ingestMessage(path: String, data: ByteArray) {
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
            val ts = json.optLong(VitalsProtocol.KEY_TS, System.currentTimeMillis())
            val availability = json.optString(VitalsProtocol.KEY_AVAILABILITY, "UNKNOWN")
            _vitals.value = WatchVitals(
                bpm = bpm,
                spo2 = spo2,
                timestampMs = ts,
                availability = availability,
            )
            Log.d(TAG, "vitals bpm=$bpm spo2=$spo2 avail=$availability")
        } catch (e: Exception) {
            Log.w(TAG, "bad vitals payload", e)
        }
    }
}
