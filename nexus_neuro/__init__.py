"""Nexus Neuro — REM sleep detection and lucid-dreaming trigger MVP."""

from nexus_neuro.models import (
    ControlMode,
    CopilotMessage,
    DetectionResult,
    EEGChunk,
    PulseReading,
    SleepStage,
)
from nexus_neuro.mock_eeg import MockEEGGenerator
from nexus_neuro.mock_pulse import MockPulseGenerator
from nexus_neuro.signal_analyzer import REMSleepDetector
from nexus_neuro.serial_trigger import SerialTrigger
from nexus_neuro.audio_voice import VoiceEngine
from nexus_neuro.copilot import AICopilot
from nexus_neuro.auth import UserRole, authenticate

__all__ = [
    "AICopilot",
    "ControlMode",
    "CopilotMessage",
    "DetectionResult",
    "EEGChunk",
    "MockEEGGenerator",
    "MockPulseGenerator",
    "PulseReading",
    "REMSleepDetector",
    "SerialTrigger",
    "SleepStage",
    "UserRole",
    "VoiceEngine",
    "authenticate",
]
