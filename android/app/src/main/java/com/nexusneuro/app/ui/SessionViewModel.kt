package com.nexusneuro.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexusneuro.app.domain.AiCopilot
import com.nexusneuro.app.domain.Auth
import com.nexusneuro.app.domain.ChartPoint
import com.nexusneuro.app.domain.Config
import com.nexusneuro.app.domain.ControlMode
import com.nexusneuro.app.domain.CopilotMessage
import com.nexusneuro.app.domain.MockEegGenerator
import com.nexusneuro.app.domain.MockPulseGenerator
import com.nexusneuro.app.domain.RemSleepDetector
import com.nexusneuro.app.domain.SleepStage
import com.nexusneuro.app.domain.UserAccount
import com.nexusneuro.app.stim.StimController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SessionViewModel : ViewModel() {
    var user by mutableStateOf<UserAccount?>(null)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set

    var controlMode by mutableStateOf(ControlMode.AUTO)
        private set
    var manualStage by mutableStateOf(SleepStage.AWAKE)
    var autoTriggerEnabled by mutableStateOf(true)
    var copilotEnabled by mutableStateOf(false)
    var sessionActive by mutableStateOf(false)
        private set

    var displayStage by mutableStateOf(SleepStage.AWAKE)
        private set
    var confidence by mutableFloatStateOf(0f)
        private set
    var currentBpm by mutableFloatStateOf(72f)
        private set
    var stimStatus by mutableStateOf("Stim: idle (USB serial v1'de yok)")
        private set

    val eegPoints = mutableStateListOf<ChartPoint>()
    val pulsePoints = mutableStateListOf<ChartPoint>()
    val copilotMessages = mutableStateListOf<CopilotMessage>()

    private val eegGenerator = MockEegGenerator()
    private val pulseGenerator = MockPulseGenerator()
    private val detector = RemSleepDetector()
    private val copilot = AiCopilot()
    private val stim = StimController()

    private val eegSamples = ArrayList<Float>()
    private val eegTimes = ArrayList<Float>()
    private var tickJob: Job? = null

    fun login(nationalId: String, password: String) {
        val account = Auth.authenticate(nationalId, password)
        if (account == null) {
            loginError = "Kimlik numarası veya şifre hatalı."
            return
        }
        loginError = null
        user = account
        controlMode = Auth.defaultMode(account.role)
        copilotEnabled = controlMode == ControlMode.COPILOT
    }

    fun logout() {
        stopSession()
        user = null
        loginError = null
    }

    fun setMode(mode: ControlMode) {
        val role = user?.role ?: return
        if (mode !in Auth.allowedModes(role)) return
        controlMode = mode
        copilotEnabled = mode == ControlMode.COPILOT
    }

    fun startSession() {
        eegGenerator.reset()
        pulseGenerator.reset()
        detector.reset()
        copilot.reset()
        stim.reset()
        eegSamples.clear()
        eegTimes.clear()
        eegPoints.clear()
        pulsePoints.clear()
        copilotMessages.clear()
        displayStage = SleepStage.AWAKE
        confidence = 0f
        currentBpm = 72f
        stimStatus = stim.statusMessage
        sessionActive = true

        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive && sessionActive) {
                tick()
                delay(Config.TICK_MS)
            }
        }
    }

    fun stopSession() {
        sessionActive = false
        tickJob?.cancel()
        tickJob = null
        stim.stopStim()
        stimStatus = stim.statusMessage
    }

    fun trigger40Hz() {
        stim.trigger40Hz()
        stimStatus = stim.statusMessage
    }

    fun stopStim() {
        stim.stopStim()
        stimStatus = stim.statusMessage
    }

    fun wakeUp() {
        stim.wakeUp()
        displayStage = SleepStage.AWAKE
        manualStage = SleepStage.AWAKE
        stimStatus = stim.statusMessage
    }

    fun statusLabel(): String {
        if (!sessionActive) return "Status: Idle — Start Session"
        if (stim.stimActive && displayStage == SleepStage.REM) return "Status: REM — 40Hz Aktif"
        if (displayStage == SleepStage.REM) return "Status: REM Detected — Triggering 40Hz!"
        return "Status: ${displayStage.label}"
    }

    fun canManual(): Boolean = user?.role?.let { Auth.canAccessManualControls(it) } == true

    fun allowedModes(): List<ControlMode> =
        user?.role?.let { Auth.allowedModes(it) }.orEmpty()

    private fun tick() {
        val chunk = eegGenerator.nextChunk()
        val chunkTimes = FloatArray(chunk.samples.size) { i ->
            chunk.timestamp + i.toFloat() / Config.SAMPLE_RATE
        }
        for (i in chunk.samples.indices) {
            eegSamples.add(chunk.samples[i])
            eegTimes.add(chunkTimes[i])
        }
        trimBuffers(eegSamples, eegTimes, Config.CHART_SECONDS)

        val analysis = samplesInWindow(eegSamples, eegTimes, Config.WINDOW_SECONDS)
        val result = detector.update(analysis)
        confidence = result.confidence

        val stageForPulse: SleepStage
        if (controlMode == ControlMode.MANUAL) {
            displayStage = manualStage
            stageForPulse = manualStage
        } else {
            displayStage = result.detectedStage
            stageForPulse = result.detectedStage
        }
        pulseGenerator.setStage(stageForPulse)

        val pulse = pulseGenerator.nextReading()
        currentBpm = pulse.bpm

        val now = System.nanoTime()
        val autoOk =
            controlMode != ControlMode.MANUAL &&
                autoTriggerEnabled &&
                result.isRem &&
                stim.cooldownOk(now)

        if (autoOk) {
            stim.trigger40Hz()
        }
        stimStatus = stim.statusMessage

        val msgs = copilot.analyze(
            mode = controlMode,
            stage = displayStage,
            bpm = pulse.bpm,
            serialConnected = false,
            stimActive = stim.stimActive,
            autoTriggerEnabled = autoTriggerEnabled,
            deviceVoiceEnabled = false,
            confidence = result.confidence,
            isRemEdge = result.isRem,
        )
        if (copilotEnabled) {
            copilotMessages.clear()
            copilotMessages.addAll(msgs.takeLast(6).reversed())
        }

        refreshCharts(pulse)
    }

    private fun refreshCharts(pulse: com.nexusneuro.app.domain.PulseReading) {
        eegPoints.clear()
        val step = maxOf(1, eegSamples.size / 200)
        var i = 0
        while (i < eegSamples.size) {
            eegPoints.add(ChartPoint(eegTimes[i], eegSamples[i]))
            i += step
        }

        // Keep pulse history in parallel lists via chart points only
        pulsePoints.add(ChartPoint(pulse.timestamp, pulse.waveformSample))
        while (pulsePoints.isNotEmpty() &&
            pulse.timestamp - pulsePoints.first().time > Config.CHART_SECONDS
        ) {
            pulsePoints.removeAt(0)
        }
    }

    private fun trimBuffers(samples: ArrayList<Float>, times: ArrayList<Float>, maxSeconds: Float) {
        if (times.isEmpty()) return
        val cutoff = times.last() - maxSeconds
        var removeCount = 0
        while (removeCount < times.size && times[removeCount] < cutoff) {
            removeCount++
        }
        if (removeCount > 0) {
            samples.subList(0, removeCount).clear()
            times.subList(0, removeCount).clear()
        }
    }

    private fun samplesInWindow(
        samples: ArrayList<Float>,
        times: ArrayList<Float>,
        windowSeconds: Float,
    ): FloatArray {
        if (times.isEmpty()) return FloatArray(0)
        val cutoff = times.last() - windowSeconds
        var start = 0
        while (start < times.size && times[start] < cutoff) start++
        return FloatArray(samples.size - start) { samples[start + it] }
    }
}
