"""
Mock EEG data generator for unity-X MVP.

Simulates real-time brainwave streams by cycling through Awake, Deep Sleep,
and REM stages with random realistic durations. Each stage produces
characteristic frequency content so the REMSleepDetector can classify them.
"""

from __future__ import annotations

import time
from typing import Optional

import numpy as np

from nexus_neuro.config import CHUNK_SIZE, SAMPLE_RATE, STAGE_DURATION_RANGES
from nexus_neuro.models import EEGChunk, SleepStage


# Ordered cycle: Awake → Deep Sleep → REM → `→ Awake …`
_STAGE_CYCLE: list[SleepStage] = [
    SleepStage.AWAKE,
    SleepStage.DEEP_SLEEP,
    SleepStage.REM,
]


class MockEEGGenerator:
    """
    Generates synthetic EEG chunks that mimic real sleep-stage waveforms.

    Waveform composition per stage:
        Awake:      Alpha (10 Hz) + Beta (20 Hz) — alert, low delta.
        Deep Sleep: Dominant Delta (1.5 Hz) — slow, high-amplitude waves.
        REM:        Theta (6 Hz) + Alpha (8 Hz) mix + occasional EOG spikes.
    """

    def __init__(
        self,
        sample_rate: int = SAMPLE_RATE,
        chunk_size: int = CHUNK_SIZE,
        rng: Optional[np.random.Generator] = None,
    ) -> None:
        self.sample_rate = sample_rate
        self.chunk_size = chunk_size
        self.rng = rng or np.random.default_rng()

        # --- Stage state machine ------------------------------------------------
        self._current_stage: SleepStage = SleepStage.AWAKE
        self._stage_index: int = 0
        self._stage_start_time: float = time.monotonic()
        self._stage_duration_sec: float = self._pick_random_duration(SleepStage.AWAKE)

        # Global sample counter drives continuous phase across chunks.
        self._total_samples: int = 0
        self._start_time: float = time.monotonic()

        # EOG spike scheduling for REM (next spike in N samples).
        self._next_eog_spike_sample: int = 0

    # ------------------------------------------------------------------ public

    def next_chunk(self) -> EEGChunk:
        """
        Produce the next chunk of simulated EEG data.

        Returns:
            EEGChunk with samples, elapsed timestamp, and ground-truth stage.
        """
        self._advance_stage_if_needed()

        samples = self._generate_chunk_for_stage(self._current_stage)
        self._total_samples += self.chunk_size

        timestamp = time.monotonic() - self._start_time
        return EEGChunk(
            samples=samples,
            timestamp=timestamp,
            true_stage=self._current_stage,
        )

    def reset(self) -> None:
        """Reset generator to initial Awake state (e.g. when user stops a session)."""
        self._current_stage = SleepStage.AWAKE
        self._stage_index = 0
        self._stage_start_time = time.monotonic()
        self._stage_duration_sec = self._pick_random_duration(SleepStage.AWAKE)
        self._total_samples = 0
        self._start_time = time.monotonic()
        self._next_eog_spike_sample = 0

    @property
    def current_stage(self) -> SleepStage:
        """Ground-truth stage currently being simulated."""
        return self._current_stage

    # ----------------------------------------------------------------- private

    def _advance_stage_if_needed(self) -> None:
        """Transition to the next stage when the random duration timer expires."""
        elapsed = time.monotonic() - self._stage_start_time
        if elapsed < self._stage_duration_sec:
            return

        # Move to next stage in the cycle.
        self._stage_index = (self._stage_index + 1) % len(_STAGE_CYCLE)
        self._current_stage = _STAGE_CYCLE[self._stage_index]
        self._stage_start_time = time.monotonic()
        self._stage_duration_sec = self._pick_random_duration(self._current_stage)

        # Schedule first EOG spike when entering REM.
        if self._current_stage is SleepStage.REM:
            self._schedule_next_eog_spike()

    def _pick_random_duration(self, stage: SleepStage) -> float:
        """Draw a random stage duration from the configured realistic range."""
        key_map = {
            SleepStage.AWAKE: "awake",
            SleepStage.DEEP_SLEEP: "deep_sleep",
            SleepStage.REM: "rem",
        }
        low, high = STAGE_DURATION_RANGES[key_map[stage]]
        return float(self.rng.uniform(low, high))

    def _time_axis(self) -> np.ndarray:
        """Continuous time axis (seconds) for the upcoming chunk."""
        start = self._total_samples / self.sample_rate
        return start + np.arange(self.chunk_size) / self.sample_rate

    def _generate_chunk_for_stage(self, stage: SleepStage) -> np.ndarray:
        """
        Synthesize one chunk of EEG-like data for the given sleep stage.

        All stages include a small noise floor to mimic sensor noise.
        """
        t = self._time_axis()
        noise = self.rng.normal(0, 3.0, size=self.chunk_size)

        if stage is SleepStage.AWAKE:
            # Alpha (10 Hz) + Beta (20 Hz): alert waking rhythm.
            alpha = 25.0 * np.sin(2 * np.pi * 10.0 * t)
            beta = 15.0 * np.sin(2 * np.pi * 20.0 * t)
            signal = alpha + beta + noise

        elif stage is SleepStage.DEEP_SLEEP:
            # Dominant delta (1.5 Hz): slow, high-amplitude slow waves.
            delta = 80.0 * np.sin(2 * np.pi * 1.5 * t)
            # Residual higher frequencies are suppressed during deep sleep.
            signal = delta + noise * 0.5

        else:  # REM
            # Mixed theta (6 Hz) + alpha (8 Hz): characteristic REM EEG.
            theta = 35.0 * np.sin(2 * np.pi * 6.0 * t)
            alpha = 20.0 * np.sin(2 * np.pi * 8.0 * t)
            signal = theta + alpha + noise
            signal = self._inject_eog_spikes(signal)

        return signal.astype(np.float64)

    def _schedule_next_eog_spike(self) -> None:
        """Set sample index for the next EOG spike (every ~2–5 s during REM)."""
        interval_sec = self.rng.uniform(2.0, 5.0)
        self._next_eog_spike_sample = self._total_samples + int(
            interval_sec * self.sample_rate
        )

    def _inject_eog_spikes(self, signal: np.ndarray) -> np.ndarray:
        """
        Add sharp EOG-like transients during REM.

        EOG (electrooculography) spikes appear as brief high-amplitude
        deflections caused by rapid eye movements during REM sleep.
        """
        chunk_start = self._total_samples
        chunk_end = chunk_start + self.chunk_size

        # Inject spikes scheduled within this chunk.
        while chunk_start <= self._next_eog_spike_sample < chunk_end:
            idx = self._next_eog_spike_sample - chunk_start
            # Sharp bipolar spike spanning ~3 samples.
            amplitude = self.rng.uniform(120.0, 200.0) * self.rng.choice([-1, 1])
            for offset in range(-1, 2):
                pos = idx + offset
                if 0 <= pos < self.chunk_size:
                    signal[pos] += amplitude * (1.0 - abs(offset) * 0.3)
            self._schedule_next_eog_spike()

        return signal
