"""
Local text-to-speech and device voice command helpers.

Uses pyttsx3 for on-device speech (Windows SAPI). All errors are caught
so missing TTS engines never crash the dashboard.
"""

from __future__ import annotations

import logging
import threading
from typing import Optional

from nexus_neuro.config import DEFAULT_VOICE_RATE, DEFAULT_VOICE_VOLUME

logger = logging.getLogger(__name__)


class VoiceEngine:
    """
    Speaks alerts locally and prepares voice payloads for the Arduino.

    Thread-safe: speech runs on a background thread so Streamlit stays responsive.
    """

    def __init__(self) -> None:
        self._engine: Optional[object] = None
        self._lock = threading.Lock()
        self._enabled = True
        self._rate = DEFAULT_VOICE_RATE
        self._volume = DEFAULT_VOICE_VOLUME
        self._last_error: Optional[str] = None
        self._init_engine()

    def _init_engine(self) -> None:
        try:
            import pyttsx3

            self._engine = pyttsx3.init()
            self._engine.setProperty("rate", self._rate)
            self._engine.setProperty("volume", self._volume)
            self._last_error = None
        except Exception as exc:  # noqa: BLE001
            self._engine = None
            self._last_error = str(exc)
            logger.warning("TTS engine unavailable: %s", exc)

    @property
    def enabled(self) -> bool:
        return self._enabled

    @enabled.setter
    def enabled(self, value: bool) -> None:
        self._enabled = value

    @property
    def volume(self) -> float:
        return self._volume

    @volume.setter
    def volume(self, value: float) -> None:
        self._volume = max(0.0, min(1.0, value))
        if self._engine is not None:
            try:
                self._engine.setProperty("volume", self._volume)
            except Exception:
                pass

    @property
    def rate(self) -> int:
        return self._rate

    @rate.setter
    def rate(self, value: int) -> None:
        self._rate = max(80, min(300, value))
        if self._engine is not None:
            try:
                self._engine.setProperty("rate", self._rate)
            except Exception:
                pass

    def status_message(self) -> str:
        if not self._enabled:
            return "Voice: Off"
        if self._engine is None:
            return f"Voice: Unavailable ({self._last_error or 'no engine'})"
        return "Voice: Ready"

    def speak(self, text: str, block: bool = False) -> bool:
        """
        Speak text locally via TTS.

        Returns True if speech was queued successfully.
        """
        if not self._enabled or not text.strip():
            return False
        if self._engine is None:
            return False

        def _run() -> None:
            with self._lock:
                try:
                    self._engine.say(text)
                    self._engine.runAndWait()
                except Exception as exc:  # noqa: BLE001
                    logger.warning("TTS speak failed: %s", exc)

        if block:
            _run()
        else:
            threading.Thread(target=_run, daemon=True).start()
        return True

    def build_device_voice_command(self, text: str) -> str:
        """Format a serial command that tells the Arduino to speak/play audio."""
        safe = text.replace("\n", " ").strip()[:120]
        return f"SPEAK:{safe}\n"
