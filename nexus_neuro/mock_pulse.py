"""
Mock pulse / heart-rate generator.

Produces BPM values and a simple pulse waveform that vary by sleep stage.
"""

from __future__ import annotations

import math
import time
from typing import Optional

import numpy as np

from nexus_neuro.config import PULSE_RANGES
from nexus_neuro.models import PulseReading, SleepStage


class MockPulseGenerator:
    """
    Simulates heart rate tied to the current sleep stage.

    Awake:      68–88 BPM — alert baseline
    Deep Sleep: 48–62 BPM — slow, steady
    REM:        62–92 BPM — slightly elevated, variable
    """

    def __init__(self, rng: Optional[np.random.Generator] = None) -> None:
        self.rng = rng or np.random.default_rng()
        self._stage: SleepStage = SleepStage.AWAKE
        self._target_bpm: float = 72.0
        self._current_bpm: float = 72.0
        self._phase: float = 0.0
        self._start_time: float = time.monotonic()
        self._last_update: float = self._start_time

    def set_stage(self, stage: SleepStage) -> None:
        """Update target BPM range when sleep stage changes."""
        if stage is self._stage:
            return
        self._stage = stage
        low, high = PULSE_RANGES[stage.config_key]
        self._target_bpm = float(self.rng.uniform(low, high))

    def next_reading(self) -> PulseReading:
        """Advance simulation one tick and return current pulse data."""
        now = time.monotonic()
        dt = now - self._last_update
        self._last_update = now

        noise = self.rng.uniform(-0.8, 0.8)
        self._current_bpm += (self._target_bpm - self._current_bpm) * 0.05 + noise
        self._current_bpm = max(40.0, min(120.0, self._current_bpm))

        beats_per_sec = self._current_bpm / 60.0
        self._phase = (self._phase + beats_per_sec * dt * 2 * math.pi) % (2 * math.pi)
        waveform = max(0.0, math.sin(self._phase)) ** 2

        return PulseReading(
            bpm=round(self._current_bpm, 1),
            timestamp=now - self._start_time,
            waveform_sample=waveform,
        )

    def reset(self) -> None:
        self._stage = SleepStage.AWAKE
        self._target_bpm = 72.0
        self._current_bpm = 72.0
        self._phase = 0.0
        self._start_time = time.monotonic()
        self._last_update = self._start_time
