package com.nexusneuro.app.domain

import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class MockPulseGenerator(
    private val rng: Random = Random.Default,
) {
    private var stage: SleepStage = SleepStage.AWAKE
    private var targetBpm: Float = 72f
    private var currentBpm: Float = 72f
    private var phase: Float = 0f
    private var startNs: Long = System.nanoTime()
    private var lastUpdateNs: Long = startNs

    fun setStage(newStage: SleepStage) {
        if (newStage == stage) return
        stage = newStage
        val (low, high) = Config.PULSE_RANGES.getValue(newStage.configKey)
        targetBpm = low + rng.nextFloat() * (high - low)
    }

    fun nextReading(): PulseReading {
        val now = System.nanoTime()
        val dt = (now - lastUpdateNs) / 1_000_000_000f
        lastUpdateNs = now

        val noise = -0.8f + rng.nextFloat() * 1.6f
        currentBpm += (targetBpm - currentBpm) * 0.05f + noise
        currentBpm = max(40f, min(120f, currentBpm))

        val beatsPerSec = currentBpm / 60f
        phase = (phase + beatsPerSec * dt * 2f * PI.toFloat()) % (2f * PI.toFloat())
        val waveform = max(0f, sin(phase)).let { it * it }

        return PulseReading(
            bpm = (currentBpm * 10f).toInt() / 10f,
            timestamp = (now - startNs) / 1_000_000_000f,
            waveformSample = waveform,
        )
    }

    fun reset() {
        stage = SleepStage.AWAKE
        targetBpm = 72f
        currentBpm = 72f
        phase = 0f
        startNs = System.nanoTime()
        lastUpdateNs = startNs
    }
}
