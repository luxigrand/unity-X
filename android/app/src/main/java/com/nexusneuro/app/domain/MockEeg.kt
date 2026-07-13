package com.nexusneuro.app.domain

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class MockEegGenerator(
    private val sampleRate: Int = Config.SAMPLE_RATE,
    private val chunkSize: Int = Config.CHUNK_SIZE,
    private val rng: Random = Random.Default,
) {
    private val stageCycle = listOf(SleepStage.AWAKE, SleepStage.DEEP_SLEEP, SleepStage.REM)

    private var currentStage: SleepStage = SleepStage.AWAKE
    private var stageIndex: Int = 0
    private var stageStartNs: Long = System.nanoTime()
    private var stageDurationSec: Float = pickRandomDuration(SleepStage.AWAKE)
    private var totalSamples: Int = 0
    private var startNs: Long = System.nanoTime()
    private var nextEogSpikeSample: Int = 0

    fun nextChunk(): EegChunk {
        advanceStageIfNeeded()
        val samples = generateChunkForStage(currentStage)
        totalSamples += chunkSize
        val timestamp = (System.nanoTime() - startNs) / 1_000_000_000f
        return EegChunk(samples = samples, timestamp = timestamp, trueStage = currentStage)
    }

    fun reset() {
        currentStage = SleepStage.AWAKE
        stageIndex = 0
        stageStartNs = System.nanoTime()
        stageDurationSec = pickRandomDuration(SleepStage.AWAKE)
        totalSamples = 0
        startNs = System.nanoTime()
        nextEogSpikeSample = 0
    }

    fun currentStage(): SleepStage = currentStage

    private fun advanceStageIfNeeded() {
        val elapsed = (System.nanoTime() - stageStartNs) / 1_000_000_000f
        if (elapsed < stageDurationSec) return

        stageIndex = (stageIndex + 1) % stageCycle.size
        currentStage = stageCycle[stageIndex]
        stageStartNs = System.nanoTime()
        stageDurationSec = pickRandomDuration(currentStage)
        if (currentStage == SleepStage.REM) {
            scheduleNextEogSpike()
        }
    }

    private fun pickRandomDuration(stage: SleepStage): Float {
        val (low, high) = Config.STAGE_DURATION_RANGES.getValue(stage.configKey)
        return low + rng.nextFloat() * (high - low)
    }

    private fun timeAxis(): FloatArray {
        val start = totalSamples.toFloat() / sampleRate
        return FloatArray(chunkSize) { i -> start + i.toFloat() / sampleRate }
    }

    private fun generateChunkForStage(stage: SleepStage): FloatArray {
        val t = timeAxis()
        val signal = FloatArray(chunkSize)
        for (i in 0 until chunkSize) {
            val noise = (rng.nextFloat() * 2f - 1f) * 3f * 1.732f // approx N(0,3) rough
            signal[i] = when (stage) {
                SleepStage.AWAKE -> {
                    val alpha = 25f * sin(2.0 * PI * 10.0 * t[i]).toFloat()
                    val beta = 15f * sin(2.0 * PI * 20.0 * t[i]).toFloat()
                    alpha + beta + noise
                }
                SleepStage.DEEP_SLEEP -> {
                    val delta = 80f * sin(2.0 * PI * 1.5 * t[i]).toFloat()
                    delta + noise * 0.5f
                }
                SleepStage.REM -> {
                    val theta = 35f * sin(2.0 * PI * 6.0 * t[i]).toFloat()
                    val alpha = 20f * sin(2.0 * PI * 8.0 * t[i]).toFloat()
                    theta + alpha + noise
                }
            }
        }
        return if (stage == SleepStage.REM) injectEogSpikes(signal) else signal
    }

    private fun scheduleNextEogSpike() {
        val intervalSec = 2f + rng.nextFloat() * 3f
        nextEogSpikeSample = totalSamples + (intervalSec * sampleRate).toInt()
    }

    private fun injectEogSpikes(signal: FloatArray): FloatArray {
        val chunkStart = totalSamples
        val chunkEnd = chunkStart + chunkSize
        while (nextEogSpikeSample in chunkStart until chunkEnd) {
            val idx = nextEogSpikeSample - chunkStart
            val sign = if (rng.nextBoolean()) 1f else -1f
            val amplitude = (120f + rng.nextFloat() * 80f) * sign
            for (offset in -1..1) {
                val pos = idx + offset
                if (pos in 0 until chunkSize) {
                    signal[pos] += amplitude * (1f - kotlin.math.abs(offset) * 0.3f)
                }
            }
            scheduleNextEogSpike()
        }
        return signal
    }
}
