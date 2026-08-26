package com.nexusneuro.app.wear

import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.PassiveListenerCallback
import androidx.health.services.client.PassiveMonitoringClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import androidx.health.services.client.data.PassiveListenerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WearVitalsUi(
    val measuring: Boolean = false,
    val bpm: Double? = null,
    val spo2: Double? = null,
    val availability: String = "UNKNOWN",
    val supportsHeartRate: Boolean = false,
    val supportsSpo2: Boolean = false,
    val supportedMeasureTypes: List<String> = emptyList(),
    val status: String = "Hazır",
    val phoneConnected: Boolean = false,
    val error: String? = null,
)

class HealthReader(
    context: android.content.Context,
    private val scope: CoroutineScope,
    private val sender: WatchTelemetrySender,
) {
    companion object {
        private const val TAG = "NexusHealth"
    }

    private val healthClient = HealthServices.getClient(context.applicationContext)
    private val measureClient: MeasureClient = healthClient.measureClient
    private val passiveClient: PassiveMonitoringClient = healthClient.passiveMonitoringClient

    private val _ui = MutableStateFlow(WearVitalsUi())
    val ui: StateFlow<WearVitalsUi> = _ui.asStateFlow()

    private val mutex = Mutex()
    private var measuring = false
    private var hrCallback: MeasureCallback? = null
    private var passiveRegistered = false
    private var pollJob: Job? = null

    /** Resolved via reflection — not on all Health Services versions. */
    private val oxygenType: DataType<*, *>? by lazy {
        try {
            @Suppress("UNCHECKED_CAST")
            DataType::class.java.getField("OXYGEN_SATURATION").get(null) as? DataType<*, *>
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun discoverCapabilities() {
        try {
            val measureCaps = measureClient.getCapabilitiesAsync().await()
            val measureSupported = measureCaps.supportedDataTypesMeasure
            val hr = DataType.HEART_RATE_BPM in measureSupported
            val names = measureSupported.map { it.name }.toMutableList()

            var spo2 = false
            try {
                val passiveCaps = passiveClient.getCapabilitiesAsync().await()
                val passiveTypes = passiveCaps.supportedDataTypesPassiveMonitoring
                names += passiveTypes.map { it.name }
                val o2 = oxygenType
                spo2 = o2 != null && o2 in passiveTypes
            } catch (e: Exception) {
                Log.w(TAG, "passive caps failed", e)
            }

            _ui.update {
                it.copy(
                    supportsHeartRate = hr,
                    supportsSpo2 = spo2,
                    supportedMeasureTypes = names.distinct().sorted(),
                    status = when {
                        hr && spo2 -> "Nabız + SpO₂ hazır"
                        hr -> "Nabız hazır · SpO₂ bu saatte yok"
                        else -> "Nabız bu cihazda yok"
                    },
                    error = null,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "capabilities failed", e)
            _ui.update {
                it.copy(error = e.message ?: "Capability hatası", status = "Hata")
            }
        }
        val connected = sender.refreshConnection()
        _ui.update { it.copy(phoneConnected = connected) }
    }

    fun start() {
        scope.launch {
            mutex.withLock {
                if (measuring) return@withLock
                measuring = true
                _ui.update { it.copy(measuring = true, status = "Ölçüm başladı", error = null) }
                registerHr()
                registerPassiveSpo2()
                pollJob?.cancel()
                pollJob = scope.launch(Dispatchers.IO) {
                    while (measuring) {
                        val connected = sender.refreshConnection()
                        _ui.update { it.copy(phoneConnected = connected) }
                        delay(2_500)
                    }
                }
            }
        }
    }

    fun stop() {
        scope.launch {
            mutex.withLock {
                measuring = false
                pollJob?.cancel()
                pollJob = null
                unregisterAll()
                _ui.update {
                    it.copy(measuring = false, status = "Durduruldu", availability = "UNKNOWN")
                }
            }
        }
    }

    private fun registerHr() {
        if (!_ui.value.supportsHeartRate) {
            _ui.update { it.copy(status = "HR desteklenmiyor") }
            return
        }
        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability,
            ) {
                val label = when (availability) {
                    is DataTypeAvailability -> availability.name
                    else -> availability.toString()
                }
                _ui.update { it.copy(availability = label) }
                pushToPhone()
            }

            override fun onDataReceived(data: DataPointContainer) {
                val points = data.getData(DataType.HEART_RATE_BPM)
                val last = points.lastOrNull() ?: return
                _ui.update {
                    it.copy(
                        bpm = last.value,
                        status = "Nabız alınıyor",
                        availability = "AVAILABLE",
                    )
                }
                pushToPhone()
            }
        }
        hrCallback = callback
        measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
    }

    private fun registerPassiveSpo2() {
        val o2 = oxygenType
        if (!_ui.value.supportsSpo2 || o2 == null) return

        val callback = object : PassiveListenerCallback {
            override fun onNewDataPointsReceived(dataPoints: DataPointContainer) {
                try {
                    // Iterate known sample points without SampleDataType compile-time dependency.
                    val samples = dataPoints.sampleDataPoints
                    for (point in samples) {
                        if (point.dataType.name.contains("OXYGEN", ignoreCase = true) ||
                            point.dataType.name.contains("SPO", ignoreCase = true)
                        ) {
                            val raw = point.value
                            val spo2Val = when (raw) {
                                is Number -> raw.toDouble()
                                else -> raw.toString().toDoubleOrNull()
                            } ?: continue
                            _ui.update { it.copy(spo2 = spo2Val) }
                            pushToPhone()
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "SpO2 parse failed", e)
                }
            }
        }
        try {
            @Suppress("UNCHECKED_CAST")
            val config = PassiveListenerConfig.builder()
                .setDataTypes(setOf(o2) as Set<DataType<*, *>>)
                .build()
            passiveClient.setPassiveListenerCallback(config, callback)
            passiveRegistered = true
            Log.i(TAG, "Passive SpO2 listener registered")
        } catch (e: Exception) {
            Log.w(TAG, "Passive SpO2 register failed", e)
            passiveRegistered = false
            _ui.update {
                it.copy(
                    supportsSpo2 = false,
                    status = "Nabız hazır · SpO₂ bu saatte yok",
                )
            }
        }
    }

    private fun pushToPhone() {
        val snap = _ui.value
        scope.launch {
            val ok = sender.sendVitals(
                bpm = snap.bpm,
                spo2 = snap.spo2,
                availability = snap.availability,
            )
            _ui.update { it.copy(phoneConnected = ok || sender.phoneConnected) }
        }
    }

    private suspend fun unregisterAll() {
        hrCallback?.let { cb ->
            try {
                measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, cb).await()
            } catch (e: Exception) {
                Log.w(TAG, "HR unregister", e)
            }
        }
        hrCallback = null
        if (passiveRegistered) {
            try {
                passiveClient.clearPassiveListenerCallbackAsync().await()
            } catch (e: Exception) {
                Log.w(TAG, "passive clear failed", e)
            }
        }
        passiveRegistered = false
    }
}
