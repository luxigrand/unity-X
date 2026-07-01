"""
Rule-based AI Co-Pilot for Nexus Neuro.

Monitors EEG stage, pulse, serial link, and control mode — then produces
actionable Turkish/English advisory messages. No external API required.
"""

from __future__ import annotations

import time
from typing import Optional

from nexus_neuro.models import ControlMode, CopilotMessage, SleepStage


class AICopilot:
    """
    Lightweight co-pilot that guides the user through sleep sessions.

    In Co-Pilot mode it can recommend manual actions; in Auto mode it
    narrates what the system is doing.
    """

    MAX_MESSAGES = 8

    def __init__(self) -> None:
        self._messages: list[CopilotMessage] = []
        self._last_stage: Optional[SleepStage] = None
        self._last_bpm: float = 0.0

    def reset(self) -> None:
        self._messages.clear()
        self._last_stage = None
        self._last_bpm = 0.0

    def get_messages(self) -> list[CopilotMessage]:
        return list(self._messages)

    def analyze(
        self,
        *,
        mode: ControlMode,
        stage: SleepStage,
        bpm: float,
        serial_connected: bool,
        stim_active: bool,
        auto_trigger_enabled: bool,
        device_voice_enabled: bool,
        confidence: float,
        is_rem_edge: bool,
    ) -> list[CopilotMessage]:
        """
        Evaluate session state and append new co-pilot messages if needed.
        """
        if mode is not ControlMode.COPILOT and mode is not ControlMode.AUTO:
            return self.get_messages()

        now = time.monotonic()

        if not serial_connected:
            self._push(
                "Arduino bağlı değil. Manuel tetikleme cihaza ulaşmayacak.",
                "warn",
                now,
            )

        if self._last_stage is not None and stage is not self._last_stage:
            self._push(
                f"Aşama değişti: {self._last_stage.value} → {stage.value}.",
                "info",
                now,
            )
            if stage is SleepStage.REM and mode is ControlMode.COPILOT:
                self._push(
                    "REM tespit edildi. 40 Hz tetiklemesi öneriliyor.",
                    "action",
                    now,
                )
            if stage is SleepStage.DEEP_SLEEP:
                self._push(
                    f"Derin uyku. Nabız düşük ({bpm:.0f} BPM) — normal.",
                    "info",
                    now,
                )

        if is_rem_edge and auto_trigger_enabled and serial_connected:
            self._push(
                "40 Hz sinyali cihaza gönderildi. Lucid dreaming protokolü aktif.",
                "action",
                now,
            )
        elif is_rem_edge and not serial_connected:
            self._push(
                "REM tespit edildi ama cihaz bağlı değil — '40Hz Tetikle' kullanın.",
                "warn",
                now,
            )

        if bpm > 95 and stage is not SleepStage.AWAKE:
            self._push(
                f"Nabız yükseldi ({bpm:.0f} BPM). Uyandırma gerekebilir.",
                "warn",
                now,
            )

        if stim_active and stage is SleepStage.AWAKE:
            self._push(
                "Uyanık durumdasınız. 'Durdur' ile stimülasyonu kapatın.",
                "warn",
                now,
            )

        if mode is ControlMode.COPILOT and not device_voice_enabled:
            self._push(
                "Cihaz sesi kapalı. Sesli geri bildirim için 'Cihaz Sesi Aç' kullanın.",
                "info",
                now,
            )

        if confidence < 0.4 and stage is SleepStage.AWAKE:
            self._push(
                "Sinyal güveni düşük. Sensör temasını kontrol edin.",
                "warn",
                now,
            )

        self._last_stage = stage
        self._last_bpm = bpm
        return self.get_messages()

    def suggest_wake_message(self, bpm: float) -> str:
        return (
            f"Günaydın. Nabzınız {bpm:.0f}. "
            "Yavaşça uyanın. Nexus Neuro uyandırma protokolü tamamlandı."
        )

    def suggest_rem_message(self) -> str:
        return "REM uykusu tespit edildi. Kırık hafif rüya protokolü başlatılıyor."

    def _push(self, text: str, priority: str, timestamp: float) -> None:
        if self._messages and self._messages[-1].text == text:
            return
        self._messages.append(CopilotMessage(text=text, priority=priority, timestamp=timestamp))
        self._messages = self._messages[-self.MAX_MESSAGES :]
