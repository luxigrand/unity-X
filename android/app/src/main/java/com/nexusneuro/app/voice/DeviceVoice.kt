package com.nexusneuro.app.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * On-device TTS for lucid / wake cues ("kişiye ses").
 */
class DeviceVoice(context: Context) : TextToSpeech.OnInitListener {
    companion object {
        private const val TAG = "NexusVoice"
    }

    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext, this)
    private var ready = false
    var enabled: Boolean = true

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            ready = false
            Log.w(TAG, "TTS init failed: $status")
            return
        }
        val engine = tts ?: return
        val tr = engine.setLanguage(Locale("tr", "TR"))
        if (tr == TextToSpeech.LANG_MISSING_DATA || tr == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.setLanguage(Locale.getDefault())
        }
        engine.setSpeechRate(0.95f)
        ready = true
    }

    fun speak(text: String) {
        if (!enabled || text.isBlank()) return
        val engine = tts
        if (!ready || engine == null) {
            Log.w(TAG, "TTS not ready — skip: $text")
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nexus-${System.nanoTime()}")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
