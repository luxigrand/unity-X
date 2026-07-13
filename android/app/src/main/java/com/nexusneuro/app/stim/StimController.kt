package com.nexusneuro.app.stim

import android.util.Log
import com.nexusneuro.app.domain.Config

/**
 * Local stimulation stub — logs commands instead of USB serial.
 * Tracks active state and trigger cooldown for auto-REM.
 */
class StimController {
    companion object {
        private const val TAG = "NexusStim"
    }

    var stimActive: Boolean = false
        private set

    var lastTriggerNs: Long = 0L
        private set

    var lastCommand: String = "—"
        private set

    val statusMessage: String
        get() = if (stimActive) {
            "Stim: 40Hz aktif (local stub) · son: ${lastCommand.trim()}"
        } else {
            "Stim: idle (USB serial v1'de yok) · son: ${lastCommand.trim()}"
        }

    fun cooldownOk(nowNs: Long = System.nanoTime()): Boolean {
        if (lastTriggerNs == 0L) return true
        val elapsedSec = (nowNs - lastTriggerNs) / 1_000_000_000f
        return elapsedSec >= Config.TRIGGER_COOLDOWN_SEC
    }

    fun trigger40Hz(): Boolean {
        lastCommand = Config.TRIGGER_COMMAND
        stimActive = true
        lastTriggerNs = System.nanoTime()
        Log.i(TAG, "TRIGGER_40HZ (stub)")
        return true
    }

    fun stopStim(): Boolean {
        lastCommand = Config.STOP_COMMAND
        stimActive = false
        Log.i(TAG, "STOP_STIM (stub)")
        return true
    }

    fun wakeUp(): Boolean {
        lastCommand = Config.WAKE_COMMAND
        stimActive = false
        Log.i(TAG, "WAKE_UP (stub)")
        return true
    }

    fun reset() {
        stimActive = false
        lastTriggerNs = 0L
        lastCommand = "—"
    }
}
