package com.nexusneuro.app.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class RemSleepDetector(
    private val sampleRate: Int = Config.SAMPLE_RATE,
) {
    private var previousStage: SleepStage = SleepStage.AWAKE
    private val minSamples: Int = sampleRate

    fun reset() {
        previousStage = SleepStage.AWAKE
    }

    fun update(samples: FloatArray): DetectionResult {
        if (samples.size < minSamples) {
            return DetectionResult(SleepStage.AWAKE, 0f, false)
        }

        val bandPowers = computeBandPowers(samples)
        val eogSpikes = countEogSpikes(samples)
        val (stage, confidence) = classify(bandPowers, eogSpikes)

        val isRem = stage == SleepStage.REM && previousStage != SleepStage.REM
        previousStage = stage

        return DetectionResult(stage, confidence, isRem)
    }

    private fun computeBandPowers(samples: FloatArray): Map<String, Float> {
        val mean = samples.average().toFloat()
        val centered = FloatArray(samples.size) { samples[it] - mean }
        val nOrig = centered.size
        val n = nextPowerOfTwo(nOrig)
        val re = FloatArray(n)
        val im = FloatArray(n)
        for (i in centered.indices) {
            re[i] = centered[i]
        }
        fftInPlace(re, im)

        val half = n / 2 + 1
        val power = FloatArray(half)
        for (k in 0 until half) {
            power[k] = (re[k] * re[k] + im[k] * im[k]) / nOrig
        }
        val freqs = FloatArray(half) { it * sampleRate.toFloat() / n }

        val bandPowers = mutableMapOf<String, Float>()
        for ((bandName, range) in Config.FREQUENCY_BANDS) {
            val (lowHz, highHz) = range
            var sum = 0f
            for (i in power.indices) {
                if (freqs[i] >= lowHz && freqs[i] < highHz) {
                    sum += power[i]
                }
            }
            bandPowers[bandName] = sum
        }
        return bandPowers
    }

    private fun countEogSpikes(samples: FloatArray): Int {
        val mean = samples.average()
        var variance = 0.0
        for (v in samples) {
            val d = v - mean
            variance += d * d
        }
        val std = sqrt(variance / samples.size).toFloat()
        if (std < 1e-6f) return 0
        val threshold = Config.EOG_SPIKE_STD_MULTIPLIER * std
        return samples.count { kotlin.math.abs(it) > threshold }
    }

    private fun classify(
        bandPowers: Map<String, Float>,
        eogSpikeCount: Int,
    ): Pair<SleepStage, Float> {
        val total = bandPowers.values.sum()
        if (total < 1e-12f) return SleepStage.AWAKE to 0f

        val deltaRatio = bandPowers.getValue("delta") / total
        val thetaRatio = bandPowers.getValue("theta") / total
        val alphaRatio = bandPowers.getValue("alpha") / total
        val betaRatio = bandPowers.getValue("beta") / total

        if (deltaRatio > Config.DELTA_DOMINANCE_THRESHOLD) {
            val confidence = min(1f, deltaRatio / Config.DELTA_DOMINANCE_THRESHOLD * 0.5f + 0.5f)
            return SleepStage.DEEP_SLEEP to confidence
        }

        if (
            thetaRatio > Config.THETA_REM_THRESHOLD &&
            eogSpikeCount >= 1 &&
            deltaRatio <= Config.DELTA_DOMINANCE_THRESHOLD
        ) {
            val confidence = min(1f, thetaRatio / Config.THETA_REM_THRESHOLD * 0.4f + 0.4f)
            return SleepStage.REM to confidence
        }

        val awakeScore = alphaRatio + betaRatio
        return SleepStage.AWAKE to min(1f, awakeScore * 1.5f)
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var v = 1
        while (v < n) v = v shl 1
        return v
    }

    private fun fftInPlace(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wlenRe = cos(ang).toFloat()
            val wlenIm = sin(ang).toFloat()
            var i = 0
            while (i < n) {
                var wRe = 1f
                var wIm = 0f
                for (k in 0 until len / 2) {
                    val uRe = re[i + k]
                    val uIm = im[i + k]
                    val vRe = re[i + k + len / 2] * wRe - im[i + k + len / 2] * wIm
                    val vIm = re[i + k + len / 2] * wIm + im[i + k + len / 2] * wRe
                    re[i + k] = uRe + vRe
                    im[i + k] = uIm + vIm
                    re[i + k + len / 2] = uRe - vRe
                    im[i + k + len / 2] = uIm - vIm
                    val nextWRe = wRe * wlenRe - wIm * wlenIm
                    wIm = wRe * wlenIm + wIm * wlenRe
                    wRe = nextWRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
