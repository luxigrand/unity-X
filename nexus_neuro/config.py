"""Shared configuration constants for the Nexus Neuro MVP."""

# --- Signal acquisition -------------------------------------------------------
SAMPLE_RATE: int = 256          # Hz — typical EEG sampling rate
CHUNK_SIZE: int = 64            # samples per tick (~250 ms at 256 Hz)
WINDOW_SECONDS: float = 4.0     # rolling analysis window for REM detection
CHART_SECONDS: float = 10.0     # seconds of waveform shown on dashboard

# --- Serial / Arduino ---------------------------------------------------------
BAUD_RATE: int = 9600
TRIGGER_COMMAND: str = "TRIGGER_40HZ\n"
STOP_COMMAND: str = "STOP_STIM\n"
WAKE_COMMAND: str = "WAKE_UP\n"
VOICE_ON_COMMAND: str = "VOICE_ON\n"
VOICE_OFF_COMMAND: str = "VOICE_OFF\n"
TRIGGER_COOLDOWN_SEC: float = 30.0  # prevent repeated triggers in one REM bout

# --- Mock sleep-stage duration ranges (seconds) -------------------------------
STAGE_DURATION_RANGES: dict[str, tuple[float, float]] = {
    "awake": (15.0, 45.0),
    "deep_sleep": (30.0, 90.0),
    "rem": (20.0, 60.0),
}

# --- Frequency band definitions (Hz) for FFT analysis -------------------------
FREQUENCY_BANDS: dict[str, tuple[float, float]] = {
    "delta": (0.5, 4.0),
    "theta": (4.0, 8.0),
    "alpha": (8.0, 13.0),
    "beta": (13.0, 30.0),
}

# --- Detection thresholds -----------------------------------------------------
DELTA_DOMINANCE_THRESHOLD: float = 0.55
THETA_REM_THRESHOLD: float = 0.25
EOG_SPIKE_STD_MULTIPLIER: float = 3.0

# --- Pulse (mock heart rate) --------------------------------------------------
PULSE_SAMPLE_RATE: float = 2.0   # BPM updates per second
PULSE_RANGES: dict[str, tuple[int, int]] = {
    "awake": (68, 88),
    "deep_sleep": (48, 62),
    "rem": (62, 92),
}

# --- Voice --------------------------------------------------------------------
DEFAULT_VOICE_RATE: int = 165    # words per minute
DEFAULT_VOICE_VOLUME: float = 1.0
