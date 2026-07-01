"""
PySerial wrapper for sending trigger commands to an Arduino.

All serial errors are caught internally so a missing or unplugged Arduino
never crashes the Streamlit application.
"""

from __future__ import annotations

import logging
from enum import Enum
from typing import Optional

import serial
from serial.tools import list_ports

from nexus_neuro.config import BAUD_RATE, TRIGGER_COMMAND

logger = logging.getLogger(__name__)


class ConnectionState(str, Enum):
    """Serial link state tracked by SerialTrigger."""

    DISCONNECTED = "disconnected"
    CONNECTED = "connected"
    ERROR = "error"


class SerialTrigger:
    """
    Manages the serial connection to an Arduino for 40 Hz tACS / LED triggering.

    Usage:
        trigger = SerialTrigger()
        if trigger.connect("COM5"):
            trigger.send_trigger()
        trigger.disconnect()
    """

    def __init__(self) -> None:
        self._port: Optional[str] = None
        self._connection: Optional[serial.Serial] = None
        self._state: ConnectionState = ConnectionState.DISCONNECTED
        self._error_message: Optional[str] = None

    # ------------------------------------------------------------------ static

    @staticmethod
    def list_available_ports() -> list[str]:
        """
        Scan the system for available COM / serial ports.

        Returns:
            Sorted list of port device names (e.g. ["COM3", "COM5"]).
        """
        try:
            ports = [p.device for p in list_ports.comports()]
            return sorted(ports)
        except Exception as exc:  # noqa: BLE001 — never crash on port scan
            logger.warning("Failed to scan serial ports: %s", exc)
            return []

    # ----------------------------------------------------------------- connect

    def connect(self, port: str, baud_rate: int = BAUD_RATE) -> bool:
        """
        Open a serial connection to the specified COM port.

        Args:
            port:      Device name (e.g. "COM5"). Must be non-empty.
            baud_rate: Serial baud rate (default from config).

        Returns:
            True on success, False on any failure (error stored internally).
        """
        self.disconnect()

        if not port or not port.strip():
            self._error_message = "No port selected"
            self._state = ConnectionState.DISCONNECTED
            return False

        port = port.strip()
        try:
            self._connection = serial.Serial(port=port, baudrate=baud_rate, timeout=1)
            self._port = port
            self._state = ConnectionState.CONNECTED
            self._error_message = None
            return True
        except (serial.SerialException, OSError, ValueError) as exc:
            self._connection = None
            self._port = port
            self._state = ConnectionState.ERROR
            self._error_message = f"Connection failed: {exc}"
            logger.warning("Could not connect to %s: %s", port, exc)
            return False

    def send_command(self, command: str) -> bool:
        """
        Send any serial command string to the connected Arduino.

        Args:
            command: Raw command (should include newline if needed).

        Returns:
            True if written successfully, False otherwise.
        """
        if self._state is not ConnectionState.CONNECTED or self._connection is None:
            logger.warning("Cannot send command — not connected")
            return False

        try:
            if not self._connection.is_open:
                self._state = ConnectionState.ERROR
                self._error_message = "Port closed unexpectedly"
                return False

            self._connection.write(command.encode("utf-8"))
            self._connection.flush()
            return True
        except (serial.SerialException, OSError, ValueError) as exc:
            self._error_message = f"Write failed: {exc}"
            self._state = ConnectionState.ERROR
            logger.warning("Serial write failed on %s: %s", self._port, exc)
            return False

    def send_trigger(self, command: str = TRIGGER_COMMAND) -> bool:
        """Send the default 40 Hz trigger command."""
        return self.send_command(command)

    @property
    def is_connected(self) -> bool:
        return self._state is ConnectionState.CONNECTED

    def disconnect(self) -> None:
        """Close the serial port safely (no-op if already disconnected)."""
        if self._connection is not None:
            try:
                if self._connection.is_open:
                    self._connection.close()
            except (serial.SerialException, OSError) as exc:
                logger.warning("Error closing serial port %s: %s", self._port, exc)
            finally:
                self._connection = None

        self._port = None
        self._state = ConnectionState.DISCONNECTED
        self._error_message = None

    # ------------------------------------------------------------------- state

    @property
    def state(self) -> ConnectionState:
        return self._state

    @property
    def port(self) -> Optional[str]:
        return self._port

    def status_message(self, selected_port: Optional[str] = None) -> str:
        """
        Human-readable connection status for the dashboard.

        Args:
            selected_port: Port chosen in the sidebar, if any.
        """
        if self._state is ConnectionState.CONNECTED and self._port:
            return f"Connected on {self._port}"
        if self._state is ConnectionState.ERROR:
            return self._error_message or "Connection error"
        if not selected_port:
            return "No port selected"
        return "Disconnected"
