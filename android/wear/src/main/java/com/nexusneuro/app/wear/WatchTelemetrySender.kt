package com.nexusneuro.app.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WatchTelemetrySender(context: Context) {
    companion object {
        private const val TAG = "NexusWearTx"
        private const val MIN_INTERVAL_MS = 900L
    }

    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)
    private var lastSentMs = 0L

    @Volatile
    var phoneConnected: Boolean = false
        private set

    suspend fun refreshConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val nodes = nodeClient.connectedNodes.await()
            phoneConnected = nodes.isNotEmpty()
            phoneConnected
        } catch (e: Exception) {
            Log.w(TAG, "node check failed", e)
            phoneConnected = false
            false
        }
    }

    suspend fun sendVitals(
        bpm: Double?,
        spo2: Double?,
        availability: String,
        force: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSentMs < MIN_INTERVAL_MS) return@withContext false
        try {
            val nodes = nodeClient.connectedNodes.await()
            phoneConnected = nodes.isNotEmpty()
            if (nodes.isEmpty()) return@withContext false

            val json = JSONObject().apply {
                put(VitalsProtocol.KEY_TS, now)
                put(VitalsProtocol.KEY_AVAILABILITY, availability)
                put(VitalsProtocol.KEY_SOURCE, "watch")
                if (bpm != null) put(VitalsProtocol.KEY_BPM, bpm)
                if (spo2 != null) put(VitalsProtocol.KEY_SPO2, spo2)
            }
            val bytes = json.toString().toByteArray(Charsets.UTF_8)
            for (node in nodes) {
                messageClient.sendMessage(node.id, VitalsProtocol.PATH, bytes).await()
            }
            lastSentMs = now
            true
        } catch (e: Exception) {
            Log.w(TAG, "send failed", e)
            phoneConnected = false
            false
        }
    }
}
