package com.nexusneuro.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexusneuro.app.auth.LoginSessionStore
import com.nexusneuro.app.auth.UnlockChallenge
import com.nexusneuro.app.domain.AiCopilot
import com.nexusneuro.app.domain.Auth
import com.nexusneuro.app.domain.ChartPoint
import com.nexusneuro.app.domain.Config
import com.nexusneuro.app.domain.ControlMode
import com.nexusneuro.app.domain.CopilotMessage
import com.nexusneuro.app.domain.MockEegGenerator
import com.nexusneuro.app.domain.MockPulseGenerator
import com.nexusneuro.app.domain.PulseReading
import com.nexusneuro.app.domain.RemSleepDetector
import com.nexusneuro.app.domain.SleepStage
import com.nexusneuro.app.domain.UserAccount
import com.nexusneuro.app.domain.UserRole
import com.nexusneuro.app.stim.StimController
import com.nexusneuro.app.voice.DeviceVoice
import com.nexusneuro.app.wear.VitalsProtocol
import com.nexusneuro.app.wear.WearVitalsRepository
import kotlinx.coroutines.Job
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class SessionViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = LoginSessionStore(application)
    private val voice = DeviceVoice(application)

    var user by mutableStateOf<UserAccount?>(null)
        private set
    var pendingUser by mutableStateOf<UserAccount?>(null)
        private set
    var unlockChallenge by mutableStateOf(UnlockChallenge.FullCredentials)
        private set
    var biometricRequired by mutableStateOf(false)
        private set
    var biometricPromptRequestId by mutableStateOf(0)
        private set
    var loginError by mutableStateOf<String?>(null)
        private set
    var savedDisplayName by mutableStateOf<String?>(null)
        private set
    var unlockCount by mutableStateOf(0)
        private set

    var controlMode by mutableStateOf(ControlMode.AUTO)
        private set
    var manualStage by mutableStateOf(SleepStage.AWAKE)
    var autoTriggerEnabled by mutableStateOf(true)
    var deviceVoiceEnabled by mutableStateOf(true)
        private set
    var copilotEnabled by mutableStateOf(false)
    var sessionActive by mutableStateOf(false)
        private set

    var displayStage by mutableStateOf(SleepStage.AWAKE)
        private set
    var confidence by mutableFloatStateOf(0f)
        private set
    var currentBpm by mutableFloatStateOf(72f)
        private set
    var currentSpo2 by mutableStateOf<Float?>(null)
        private set
    var pulseSource by mutableStateOf("Mock")
        private set
    var stimStatus by mutableStateOf("Stim: idle (USB serial v1'de yok)")
        private set

    /** Simple user home (non-admin): live watch vitals without EEG session. */
    var watchConnected by mutableStateOf(false)
        private set
    var liveBpm by mutableStateOf<Float?>(null)
        private set
    var liveSpo2 by mutableStateOf<Float?>(null)
        private set
    var userVitalsStatus by mutableStateOf("Ölçüm bekleniyor")
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
    private var userVitalsJob: Job? = null
    private var lastSpokenRemNs: Long = 0L
    private var watchWavePhase: Float = 0f
    private var sessionStartNs: Long = System.nanoTime()
    private var lastTickNs: Long = sessionStartNs

    fun isAdminUser(): Boolean = user?.role == UserRole.ADMIN

    init {
        refreshUnlockState(autoPrompt = true)
        voice.enabled = deviceVoiceEnabled
    }

    override fun onCleared() {
        voice.shutdown()
        super.onCleared()
    }

    fun toggleDeviceVoice(enabled: Boolean) {
        deviceVoiceEnabled = enabled
        voice.enabled = enabled
    }

    private fun refreshUnlockState(autoPrompt: Boolean) {
        savedDisplayName = sessionStore.savedDisplayName
        unlockCount = sessionStore.unlockCount
        unlockChallenge = sessionStore.challengeForNextUnlock()
        loginError = null
        pendingUser = null
        biometricRequired = false

        when (unlockChallenge) {
            UnlockChallenge.FullCredentials -> {
                // Show kimlik + şifre form (first time or every 20th).
            }
            UnlockChallenge.SingleBiometric,
            UnlockChallenge.DualBiometric,
            -> {
                val id = sessionStore.savedNationalId
                val account = id?.let { Auth.findByNationalId(it) }
                if (account == null) {
                    sessionStore.clearAccount()
                    unlockChallenge = UnlockChallenge.FullCredentials
                    savedDisplayName = null
                    unlockCount = 0
                } else {
                    pendingUser = account
                    biometricRequired = true
                    if (autoPrompt) biometricPromptRequestId += 1
                }
            }
        }
    }

    fun login(nationalId: String, password: String) {
        val account = Auth.authenticate(nationalId, password)
        if (account == null) {
            loginError = "Kimlik numarası veya şifre hatalı."
            return
        }
        val saved = sessionStore.savedNationalId
        if (saved != null && saved != account.nationalId) {
            sessionStore.clearAccount()
            unlockCount = 0
        }
        loginError = null
        pendingUser = account
        unlockChallenge = UnlockChallenge.SingleBiometric
        biometricRequired = true
        biometricPromptRequestId += 1
    }

    fun onBiometricSuccess() {
        val account = pendingUser ?: return
        pendingUser = null
        biometricRequired = false
        loginError = null
        sessionStore.rememberSuccessfulUnlock(account.nationalId, account.displayName)
        savedDisplayName = account.displayName
        unlockCount = sessionStore.unlockCount
        user = account
        controlMode = Auth.defaultMode(account.role)
        // AI / Lucid cues visible in every mode (panel fills as events fire).
        copilotEnabled = true
        deviceVoiceEnabled = true
        voice.enabled = true
        if (account.role != UserRole.ADMIN) {
            startUserVitalsWatch()
        } else {
            stopUserVitalsWatch()
        }
    }

    fun onBiometricFailure(message: String) {
        loginError = message
        biometricRequired = pendingUser != null
    }

    fun switchAccount() {
        stopSession()
        stopUserVitalsWatch()
        user = null
        sessionStore.clearAccount()
        savedDisplayName = null
        unlockCount = 0
        unlockChallenge = UnlockChallenge.FullCredentials
        pendingUser = null
        biometricRequired = false
        loginError = null
    }

    fun logout() {
        stopSession()
        stopUserVitalsWatch()
        user = null
        refreshUnlockState(autoPrompt = true)
    }

    private fun startUserVitalsWatch() {
        stopUserVitalsWatch()
        userVitalsJob = viewModelScope.launch {
            while (isActive) {
                applyUserVitals(WearVitalsRepository.vitals.value)
                delay(500)
            }
        }
    }

    private fun stopUserVitalsWatch() {
        userVitalsJob?.cancel()
        userVitalsJob = null
        watchConnected = false
        liveBpm = null
        liveSpo2 = null
        userVitalsStatus = "Ölçüm bekleniyor"
    }

    private fun applyUserVitals(watch: com.nexusneuro.app.wear.WatchVitals) {
        if (watch.isFresh() && watch.bpm != null) {
            watchConnected = true
            liveBpm = watch.bpm
            liveSpo2 = watch.spo2
            userVitalsStatus = "Canlı nabız"
        } else {
            watchConnected = false
            // Keep last BPM briefly visible only while still within a soft window; otherwise clear.
            val age = if (watch.timestampMs > 0L) {
                System.currentTimeMillis() - watch.timestampMs
            } else {
                Long.MAX_VALUE
            }
            if (age > VitalsProtocol.FRESH_MS * 2) {
                liveBpm = null
                liveSpo2 = null
            }
            userVitalsStatus = "Saatte unity-X → BAŞLAT"
        }
    }

    fun requestBiometricAgain() {
        if (pendingUser == null && unlockChallenge != UnlockChallenge.FullCredentials) {
            refreshUnlockState(autoPrompt = false)
        }
        if (pendingUser != null) {
            biometricRequired = true
            biometricPromptRequestId += 1
        }
    }

    fun setMode(mode: ControlMode) {
        val role = user?.role ?: return
        if (mode !in Auth.allowedModes(role)) return
        controlMode = mode
        copilotEnabled = true
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
        currentSpo2 = null
        pulseSource = "Mock"
        stimStatus = stim.statusMessage
        lastSpokenRemNs = 0L
        watchWavePhase = 0f
        sessionStartNs = System.nanoTime()
        lastTickNs = sessionStartNs
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
        stimStatus = "Stim: 40Hz · Lucid dreaming protokolü aktif"
        speakRemCue()
    }

    fun stopStim() {
        stim.stopStim()
        stimStatus = stim.statusMessage
    }

    fun wakeUp() {
        val msg = copilot.suggestWakeMessage(currentBpm)
        stim.wakeUp()
        displayStage = SleepStage.AWAKE
        manualStage = SleepStage.AWAKE
        stimStatus = stim.statusMessage
        voice.speak(msg)
    }

    fun testVoice() {
        voice.speak("unity-X ses testi. Kişiye ses aktif.")
    }

    fun statusLabel(): String {
        if (!sessionActive) return "Status: Idle — Start Session"
        if (stim.stimActive && displayStage == SleepStage.REM) return "Status: REM — Lucid 40Hz Aktif"
        if (displayStage == SleepStage.REM) return "Status: REM Detected — Triggering 40Hz!"
        return "Status: ${displayStage.label}"
    }

    fun canManual(): Boolean = user?.role?.let { Auth.canAccessManualControls(it) } == true

    fun showManualPanel(): Boolean =
        canManual() && controlMode == ControlMode.MANUAL

    fun allowedModes(): List<ControlMode> =
        user?.role?.let { Auth.allowedModes(it) }.orEmpty()

    private fun speakRemCue() {
        val now = System.nanoTime()
        if (lastSpokenRemNs != 0L) {
            val elapsed = (now - lastSpokenRemNs) / 1_000_000_000f
            if (elapsed < Config.TRIGGER_COOLDOWN_SEC) return
        }
        lastSpokenRemNs = now
        voice.speak(copilot.suggestRemMessage())
    }

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

        val watch = WearVitalsRepository.vitals.value
        val pulse: PulseReading
        if (watch.isFresh() && watch.bpm != null) {
            pulse = pulseFromWatch(watch.bpm)
            pulseSource = "Saat"
            currentSpo2 = watch.spo2
        } else {
            pulse = pulseGenerator.nextReading()
            pulseSource = "Mock"
            currentSpo2 = if (watch.isFresh()) watch.spo2 else null
        }
        currentBpm = pulse.bpm

        val now = System.nanoTime()
        val autoOk =
            controlMode != ControlMode.MANUAL &&
                autoTriggerEnabled &&
                result.isRem &&
                stim.cooldownOk(now)

        if (autoOk) {
            stim.trigger40Hz()
            stimStatus = "Stim: 40Hz · Lucid dreaming protokolü aktif"
            speakRemCue()
        } else {
            stimStatus = stim.statusMessage
        }

        val msgs = copilot.analyze(
            mode = controlMode,
            stage = displayStage,
            bpm = pulse.bpm,
            serialConnected = false,
            stimActive = stim.stimActive,
            autoTriggerEnabled = autoTriggerEnabled,
            deviceVoiceEnabled = deviceVoiceEnabled,
            confidence = result.confidence,
            isRemEdge = result.isRem,
        )
        if (copilotEnabled) {
            copilotMessages.clear()
            copilotMessages.addAll(msgs.takeLast(6).reversed())
        }

        refreshCharts(pulse)
    }

    /** Build a chart sample driven by live watch BPM. */
    private fun pulseFromWatch(bpm: Float): PulseReading {
        val now = System.nanoTime()
        val dt = (now - lastTickNs) / 1_000_000_000f
        lastTickNs = now
        val beatsPerSec = bpm / 60f
        watchWavePhase = (watchWavePhase + beatsPerSec * dt * 2f * PI.toFloat()) % (2f * PI.toFloat())
        val waveform = max(0f, sin(watchWavePhase)).let { it * it }
        return PulseReading(
            bpm = bpm,
            timestamp = (now - sessionStartNs) / 1_000_000_000f,
            waveformSample = waveform,
        )
    }

    private fun refreshCharts(pulse: com.nexusneuro.app.domain.PulseReading) {
        eegPoints.clear()
        val step = maxOf(1, eegSamples.size / 200)
        var i = 0
        while (i < eegSamples.size) {
            eegPoints.add(ChartPoint(eegTimes[i], eegSamples[i]))
            i += step
        }

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
