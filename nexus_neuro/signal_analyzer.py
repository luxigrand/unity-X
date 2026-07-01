"""
REM sleep detection via FFT band-power analysis.

Analyzes a rolling window of EEG samples and classifies the current sleep
stage. Fires an edge-triggered REM flag once per REM onset for Arduino triggering.
"""

from __future__ import annotations

import numpy as np

from nexus_neuro.config import (
    DELTA_DOMINANCE_THRESHOLD,
    EOG_SPIKE_STD_MULTIPLIER,
    FREQUENCY_BANDS,
    SAMPLE_RATE,
    THETA_REM_THRESHOLD,
)
from nexus_neuro.models import DetectionResult, SleepStage


class REMSleepDetector:
    """
    Continuously classifies incoming EEG windows into sleep stages.

    Classification pipeline:
        1. FFT → band power (delta, theta, alpha, beta)
        2. EOG spike count (samples > N × std)
        3. Rule-based stage assignment
        4. Edge detection for REM onset (trigger once per bout)
    """

    def __init__(self, sample_rate: int = SAMPLE_RATE) -> None:
        self.sample_rate = sample_rate
        self._previous_stage: SleepStage = SleepStage.AWAKE
        self._min_samples: int = sample_rate  # need ≥1 s before classifying

    def reset(self) -> None:
        """Clear internal state (call when session stops)."""
        self._previous_stage = SleepStage.AWAKE

    def update(self, samples: np.ndarray) -> DetectionResult:
        """
        Analyze a rolling buffer and return the detected sleep stage.

        Args:
            samples: 1-D array of the most recent EEG samples (≥1 s recommended).

        Returns:
            DetectionResult with stage, confidence, and edge-triggered is_rem flag.
        """
        if len(samples) < self._min_samples:
            return DetectionResult(
                detected_stage=SleepStage.AWAKE,
                confidence=0.0,
                is_rem=False,
            )

        band_powers = self._compute_band_powers(samples)
        eog_spikes = self._count_eog_spikes(samples)
        stage, confidence = self._classify(band_powers, eog_spikes)

        # Edge-trigger: True only on transition *into* REM.
        is_rem = stage is SleepStage.REM and self._previous_stage is not SleepStage.REM
        self._previous_stage = stage

        return DetectionResult(
            detected_stage=stage,
            confidence=confidence,
            is_rem=is_rem,
        )

    # ----------------------------------------------------------------- private

    def _compute_band_powers(self, samples: np.ndarray) -> dict[str, float]:
        """
        Compute absolute power in each EEG frequency band via FFT.

        Returns power values keyed by band name (not normalised yet).
        """
        # Remove DC offset before spectral analysis.
        centered = samples - np.mean(samples)
        n = len(centered)

        # Real FFT → one-sided frequency bins.
        fft_vals = np.fft.rfft(centered)
        power_spectrum = (np.abs(fft_vals) ** 2) / n
        freqs = np.fft.rfftfreq(n, d=1.0 / self.sample_rate)

        band_powers: dict[str, float] = {}
        for band_name, (low_hz, high_hz) in FREQUENCY_BANDS.items():
            mask = (freqs >= low_hz) & (freqs < high_hz)
            band_powers[band_name] = float(np.sum(power_spectrum[mask]))

        return band_powers

    def _count_eog_spikes(self, samples: np.ndarray) -> int:
        """
        Count EOG-like spikes: samples whose amplitude exceeds N × std.

        REM sleep is characterised by rapid eye movements that produce
        brief high-amplitude transients in frontal EEG channels.
        """
        std = float(np.std(samples))
        if std < 1e-6:
            return 0
        threshold = EOG_SPIKE_STD_MULTIPLIER * std
        return int(np.sum(np.abs(samples) > threshold))

    def _classify(
        self,
        band_powers: dict[str, float],
        eog_spike_count: int,
    ) -> tuple[SleepStage, float]:
        """
        Apply rule-based classification from band-power ratios.

        Rules (evaluated in priority order):
            Deep Sleep: delta / total > DELTA_DOMINANCE_THRESHOLD
            REM:        theta / total > THETA_REM_THRESHOLD
                        AND ≥1 EOG spike AND delta not dominant
            Awake:      default (alpha + beta dominant)
        """
        total = sum(band_powers.values())
        if total < 1e-12:
            return SleepStage.AWAKE, 0.0

        delta_ratio = band_powers["delta"] / total
        theta_ratio = band_powers["theta"] / total
        alpha_ratio = band_powers["alpha"] / total
        beta_ratio = band_powers["beta"] / total

        # --- Deep Sleep: slow-wave dominance ----------------------------------
        if delta_ratio > DELTA_DOMINANCE_THRESHOLD:
            confidence = min(1.0, delta_ratio / DELTA_DOMINANCE_THRESHOLD * 0.5 + 0.5)
            return SleepStage.DEEP_SLEEP, confidence

        # --- REM: elevated theta + eye-movement spikes ------------------------
        if (
            theta_ratio > THETA_REM_THRESHOLD
            and eog_spike_count >= 1
            and delta_ratio <= DELTA_DOMINANCE_THRESHOLD
        ):
            confidence = min(1.0, theta_ratio / THETA_REM_THRESHOLD * 0.4 + 0.4)
            return SleepStage.REM, confidence

        # --- Awake: higher-frequency alpha/beta dominance ---------------------
        awake_score = alpha_ratio + beta_ratio
        confidence = min(1.0, awake_score * 1.5)
        return SleepStage.AWAKE, confidence
