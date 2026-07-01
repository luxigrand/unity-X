"""Data models and enums used across the Nexus Neuro application."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Optional

import numpy as np


class SleepStage(str, Enum):
    """Canonical sleep stages simulated and detected by the MVP."""

    AWAKE = "Awake"
    DEEP_SLEEP = "Deep Sleep"
    REM = "REM Sleep"

    @property
    def display_label(self) -> str:
        if self is SleepStage.REM:
            return "REM Detected — Triggering 40Hz!"
        return self.value

    @property
    def config_key(self) -> str:
        """Key used in config dicts (pulse ranges, etc.)."""
        mapping = {
            SleepStage.AWAKE: "awake",
            SleepStage.DEEP_SLEEP: "deep_sleep",
            SleepStage.REM: "rem",
        }
        return mapping[self]


class ControlMode(str, Enum):
    """How the system drives stimulation and stage logic."""

    AUTO = "Auto"
    MANUAL = "Manual"
    COPILOT = "AI Co-Pilot"


@dataclass(frozen=True)
class EEGChunk:
    samples: np.ndarray
    timestamp: float
    true_stage: SleepStage


@dataclass(frozen=True)
class PulseReading:
    """Instantaneous heart-rate reading from mock (or future real) sensor."""

    bpm: float
    timestamp: float
    waveform_sample: float  # normalized 0–1 pulse wave tick


@dataclass(frozen=True)
class DetectionResult:
    detected_stage: SleepStage
    confidence: float
    is_rem: bool


@dataclass
class CopilotMessage:
    """A single AI co-pilot advisory line shown in the dashboard."""

    text: str
    priority: str = "info"  # info | warn | action
    timestamp: float = 0.0


@dataclass
class SessionControls:
    """
    Runtime control flags toggled from the manual control panel.

    Stored in Streamlit session_state as a dict-like bundle.
    """

    mode: ControlMode = ControlMode.AUTO
    manual_stage: SleepStage = SleepStage.AWAKE
    auto_trigger_enabled: bool = True
    device_voice_enabled: bool = True
    local_voice_enabled: bool = True
    copilot_enabled: bool = True
    stim_active: bool = False
    last_copilot_messages: list[CopilotMessage] = field(default_factory=list)
