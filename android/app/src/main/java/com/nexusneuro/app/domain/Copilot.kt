package com.nexusneuro.app.domain

class AiCopilot {
    companion object {
        const val MAX_MESSAGES = 8
    }

    private val messages = mutableListOf<CopilotMessage>()
    private var lastStage: SleepStage? = null
    private var lastBpm: Float = 0f
    private var voiceHintShown = false

    fun reset() {
        messages.clear()
        lastStage = null
        lastBpm = 0f
        voiceHintShown = false
    }

    fun getMessages(): List<CopilotMessage> = messages.toList()

    fun analyze(
        mode: ControlMode,
        stage: SleepStage,
        bpm: Float,
        serialConnected: Boolean,
        stimActive: Boolean,
        autoTriggerEnabled: Boolean,
        deviceVoiceEnabled: Boolean,
        confidence: Float,
        isRemEdge: Boolean,
    ): List<CopilotMessage> {
        // Manual: still surface REM / Lucid cues when stage chips or stim change.
        if (mode != ControlMode.COPILOT && mode != ControlMode.AUTO && mode != ControlMode.MANUAL) {
            return getMessages()
        }

        val now = System.nanoTime() / 1_000_000_000f

        if (mode != ControlMode.MANUAL && !serialConnected) {
            push("Arduino bağlı değil (mobil stub). Stimülasyon yerel olarak izlenir.", "warn", now)
        }

        val prev = lastStage
        if (prev != null && stage != prev) {
            push("Aşama değişti: ${prev.label} → ${stage.label}.", "info", now)
            if (stage == SleepStage.REM) {
                if (mode == ControlMode.COPILOT || mode == ControlMode.MANUAL) {
                    push("REM tespit edildi. Lucid dreaming için 40 Hz öneriliyor.", "action", now)
                }
            }
            if (stage == SleepStage.DEEP_SLEEP) {
                push("Derin uyku. Nabız düşük (${bpm.toInt()} BPM) — normal.", "info", now)
            }
        }

        when {
            isRemEdge && autoTriggerEnabled && mode != ControlMode.MANUAL && serialConnected ->
                push("40 Hz sinyali cihaza gönderildi. Lucid dreaming protokolü aktif.", "action", now)
            isRemEdge && autoTriggerEnabled && mode != ControlMode.MANUAL ->
                push("40 Hz yerel tetiklendi. Lucid dreaming protokolü aktif.", "action", now)
            isRemEdge && mode != ControlMode.MANUAL && !autoTriggerEnabled ->
                push("REM tespit edildi — otomatik tetikleme kapalı.", "warn", now)
            stimActive && stage == SleepStage.REM && mode == ControlMode.MANUAL ->
                push("Manuel 40 Hz aktif. Lucid dreaming protokolü çalışıyor.", "action", now)
        }

        if (bpm > 95f && stage != SleepStage.AWAKE) {
            push("Nabız yükseldi (${bpm.toInt()} BPM). Uyandırma gerekebilir.", "warn", now)
        }

        if (stimActive && stage == SleepStage.AWAKE) {
            push("Uyanık durumdasınız. 'Durdur' ile stimülasyonu kapatın.", "warn", now)
        }

        if (mode == ControlMode.COPILOT && !deviceVoiceEnabled && !voiceHintShown) {
            push("Kişiye ses kapalı. Sesli bildirim için 'Kişiye Ses' açın.", "info", now)
            voiceHintShown = true
        }

        if (confidence < 0.4f && stage == SleepStage.AWAKE) {
            push("Sinyal güveni düşük. Sensör temasını kontrol edin.", "warn", now)
        }

        lastStage = stage
        lastBpm = bpm
        return getMessages()
    }

    fun suggestWakeMessage(bpm: Float): String =
        "Günaydın. Nabzınız ${bpm.toInt()}. Yavaşça uyanın. unity-X uyandırma protokolü tamamlandı."

    fun suggestRemMessage(): String =
        "REM uykusu tespit edildi. Lucid rüya protokolü başlatılıyor."

    private fun push(text: String, priority: String, timestamp: Float) {
        if (messages.isNotEmpty() && messages.last().text == text) return
        messages.add(CopilotMessage(text, priority, timestamp))
        while (messages.size > MAX_MESSAGES) {
            messages.removeAt(0)
        }
    }
}
