"""
unity-X — Phase 1 MVP Dashboard.

Run as desktop app:  python main.py
Run in browser:      python -m streamlit run app.py
"""

from __future__ import annotations

import time
from datetime import timedelta

import numpy as np
import plotly.graph_objects as go
import streamlit as st

from nexus_neuro.audio_voice import VoiceEngine
from nexus_neuro.auth import (
    UserRole,
    allowed_modes,
    authenticate,
    can_access_manual_controls,
    default_mode,
)
from nexus_neuro.cloud_vitals import config_status as cloud_config_status
from nexus_neuro.cloud_vitals import fetch_latest_vitals
from nexus_neuro.config import (
    CHART_SECONDS,
    SAMPLE_RATE,
    STOP_COMMAND,
    TRIGGER_COMMAND,
    TRIGGER_COOLDOWN_SEC,
    VOICE_OFF_COMMAND,
    VOICE_ON_COMMAND,
    WAKE_COMMAND,
    WINDOW_SECONDS,
)
from nexus_neuro.copilot import AICopilot
from nexus_neuro.mock_eeg import MockEEGGenerator
from nexus_neuro.mock_pulse import MockPulseGenerator
from nexus_neuro.models import ControlMode, SleepStage
from nexus_neuro.serial_trigger import SerialTrigger
from nexus_neuro.signal_analyzer import REMSleepDetector

st.set_page_config(
    page_title="unity-X",
    page_icon="◉",
    layout="wide",
    initial_sidebar_state="expanded",
)

BW_CSS = """
<style>
    .stApp, [data-testid="stAppViewContainer"], [data-testid="stHeader"] {
        background-color: #000000 !important;
        color: #FFFFFF !important;
    }
    [data-testid="stSidebar"] {
        background-color: #0a0a0a !important;
        border-right: 1px solid #FFFFFF !important;
    }
    [data-testid="stSidebar"] * { color: #FFFFFF !important; }
    h1, h2, h3, p, label, span, .stMarkdown {
        color: #FFFFFF !important;
        font-family: "Courier New", Courier, monospace !important;
    }
    .status-banner {
        font-size: 2.2rem;
        font-weight: 700;
        letter-spacing: 0.08em;
        text-transform: uppercase;
        color: #FFFFFF;
        border: 2px solid #FFFFFF;
        padding: 1rem 1.2rem;
        margin: 0.5rem 0;
        font-family: "Courier New", Courier, monospace;
    }
    .pulse-box {
        font-size: 3rem;
        font-weight: 700;
        color: #FFFFFF;
        border: 2px solid #FFFFFF;
        padding: 1rem;
        text-align: center;
        font-family: "Courier New", Courier, monospace;
    }
    .pulse-label {
        font-size: 0.9rem;
        letter-spacing: 0.2em;
        opacity: 0.8;
    }
    .serial-status, .copilot-line {
        font-size: 0.95rem;
        color: #FFFFFF;
        opacity: 0.9;
        font-family: "Courier New", Courier, monospace;
        margin-bottom: 0.4rem;
    }
    .copilot-box {
        border: 1px solid #FFFFFF;
        padding: 0.8rem 1rem;
        margin-top: 0.5rem;
        max-height: 180px;
        overflow-y: auto;
    }
    .stButton > button {
        background-color: #000000 !important;
        color: #FFFFFF !important;
        border: 1px solid #FFFFFF !important;
        font-family: "Courier New", Courier, monospace !important;
        text-transform: uppercase;
    }
    .stButton > button:hover {
        background-color: #FFFFFF !important;
        color: #000000 !important;
    }
    .stSelectbox > div > div, [data-baseweb="select"] {
        background-color: #000000 !important;
        border-color: #FFFFFF !important;
        color: #FFFFFF !important;
    }
    [data-testid="stDecoration"], #MainMenu, footer, header { visibility: hidden; }
    .login-box {
        max-width: 420px;
        margin: 4rem auto;
        border: 2px solid #FFFFFF;
        padding: 2rem;
        font-family: "Courier New", Courier, monospace;
    }
    .login-title {
        font-size: 1.8rem;
        letter-spacing: 0.15em;
        text-align: center;
        margin-bottom: 1.5rem;
    }
    .stAlert {
        background-color: #111111 !important;
        color: #FFFFFF !important;
        border: 1px solid #FFFFFF !important;
    }
</style>
"""
st.markdown(BW_CSS, unsafe_allow_html=True)


def _init_session_state() -> None:
    defaults = {
        "authenticated": False,
        "user_role": None,
        "user_name": None,
        "user_national_id": None,
        "session_active": False,
        "eeg_generator": MockEEGGenerator(),
        "pulse_generator": MockPulseGenerator(),
        "detector": REMSleepDetector(),
        "serial_trigger": SerialTrigger(),
        "voice_engine": VoiceEngine(),
        "copilot": AICopilot(),
        "eeg_buffer": np.array([], dtype=np.float64),
        "time_buffer": np.array([], dtype=np.float64),
        "pulse_buffer": np.array([], dtype=np.float64),
        "pulse_time_buffer": np.array([], dtype=np.float64),
        "detected_stage": SleepStage.AWAKE,
        "display_stage": SleepStage.AWAKE,
        "current_bpm": 72.0,
        "last_trigger_time": 0.0,
        "trigger_sent": False,
        "selected_port": None,
        "control_mode": ControlMode.AUTO,
        "manual_stage": SleepStage.AWAKE,
        "auto_trigger_enabled": True,
        "device_voice_enabled": True,
        "local_voice_enabled": True,
        "copilot_enabled": True,
        "stim_active": False,
        "copilot_messages": [],
        "voice_message": "unity-X hazır.",
    }
    for key, value in defaults.items():
        if key not in st.session_state:
            st.session_state[key] = value


_init_session_state()


def _render_login() -> None:
    """Personnel login screen — blocks dashboard until authenticated."""
    st.markdown(
        '<div class="login-box"><div class="login-title">UNITY-X</div></div>',
        unsafe_allow_html=True,
    )
    st.markdown("### Personel Girişi")

    col1, col2, col3 = st.columns([1, 1.2, 1])
    with col2:
        national_id = st.text_input("Kimlik Numarası", max_chars=11, key="login_id")
        password = st.text_input("Şifre", type="password", key="login_pw")
        if st.button("GİRİŞ", use_container_width=True, type="primary"):
            user = authenticate(national_id, password)
            if user:
                st.session_state.authenticated = True
                st.session_state.user_role = user.role
                st.session_state.user_name = user.display_name
                st.session_state.user_national_id = user.national_id
                st.session_state.control_mode = default_mode(user.role)
                st.session_state.copilot_enabled = (
                    st.session_state.control_mode is ControlMode.COPILOT
                )
                st.session_state.session_active = False
                st.rerun()
            else:
                st.error("Kimlik numarası veya şifre hatalı.")

        st.caption(
            "Administrator / Sunum → Manual+Auto+Co-Pilot · Personel → Auto/Co-Pilot\n"
            "Son kullanıcı (e-posta) → Android consumer uygulaması"
        )


def _logout() -> None:
    st.session_state.authenticated = False
    st.session_state.user_role = None
    st.session_state.user_name = None
    st.session_state.user_national_id = None
    st.session_state.session_active = False
    st.session_state.serial_trigger.disconnect()
    st.rerun()


def _enforce_role_mode() -> None:
    """Ensure current control mode is allowed for the logged-in role."""
    role: UserRole = st.session_state.user_role
    permitted = allowed_modes(role)
    if st.session_state.control_mode not in permitted:
        st.session_state.control_mode = default_mode(role)
    st.session_state.copilot_enabled = True


if not st.session_state.authenticated:
    _render_login()
    st.stop()

_enforce_role_mode()


def _trim_buffer(samples, times, max_seconds):
    if len(times) == 0:
        return samples, times
    cutoff = times[-1] - max_seconds
    mask = times >= cutoff
    return samples[mask], times[mask]


def _build_line_chart(times, samples, ytitle, height=320):
    fig = go.Figure()
    fig.add_trace(
        go.Scatter(x=times, y=samples, mode="lines", line=dict(color="#FFFFFF", width=1))
    )
    fig.update_layout(
        paper_bgcolor="#000000",
        plot_bgcolor="#000000",
        font=dict(color="#FFFFFF", family="Courier New, monospace", size=11),
        xaxis=dict(title="Time (s)", color="#FFFFFF", gridcolor="#333333"),
        yaxis=dict(title=ytitle, color="#FFFFFF", gridcolor="#333333"),
        margin=dict(l=50, r=20, t=30, b=40),
        height=height,
        showlegend=False,
    )
    return fig


def _status_label(stage: SleepStage, stim_active: bool) -> str:
    if stim_active and stage is SleepStage.REM:
        return "Status: REM — Lucid 40Hz Aktif"
    if stage is SleepStage.REM:
        return "Status: REM Detected — Triggering 40Hz!"
    return f"Status: {stage.value}"


def _speak_local(text: str) -> None:
    if st.session_state.local_voice_enabled:
        st.session_state.voice_engine.enabled = True
        st.session_state.voice_engine.speak(text)


def _send_device_voice(text: str) -> bool:
    trigger: SerialTrigger = st.session_state.serial_trigger
    voice: VoiceEngine = st.session_state.voice_engine
    if not st.session_state.device_voice_enabled:
        return False
    cmd = voice.build_device_voice_command(text)
    return trigger.send_command(cmd)


def _do_trigger_40hz() -> bool:
    trigger: SerialTrigger = st.session_state.serial_trigger
    ok = trigger.send_command(TRIGGER_COMMAND)
    if ok:
        st.session_state.stim_active = True
        st.session_state.last_trigger_time = time.monotonic()
        st.session_state.trigger_sent = True
        msg = st.session_state.copilot.suggest_rem_message()
        _speak_local(msg)
        if st.session_state.device_voice_enabled:
            trigger.send_command(VOICE_ON_COMMAND)
            _send_device_voice(msg)
    return ok


def _do_stop_stim() -> bool:
    trigger: SerialTrigger = st.session_state.serial_trigger
    ok = trigger.send_command(STOP_COMMAND)
    st.session_state.stim_active = False
    _speak_local("Stimülasyon durduruldu.")
    return ok


def _do_wake_up() -> bool:
    trigger: SerialTrigger = st.session_state.serial_trigger
    bpm = st.session_state.current_bpm
    msg = st.session_state.copilot.suggest_wake_message(bpm)
    ok = trigger.send_command(WAKE_COMMAND)
    st.session_state.stim_active = False
    st.session_state.display_stage = SleepStage.AWAKE
    st.session_state.manual_stage = SleepStage.AWAKE
    _speak_local(msg)
    if st.session_state.device_voice_enabled:
        trigger.send_command(VOICE_ON_COMMAND)
        _send_device_voice(msg)
    return ok


# ---------------------------------------------------------------------------
# Sidebar
# ---------------------------------------------------------------------------
with st.sidebar:
    st.markdown("## UNITY-X")
    st.caption(f"{st.session_state.user_name} · {st.session_state.user_role.value}")
    if st.button("Çıkış", use_container_width=True):
        _logout()
    st.markdown("---")

    st.markdown("**Kontrol Modu**")
    role: UserRole = st.session_state.user_role
    mode_options = [m.value for m in allowed_modes(role)]
    current_idx = mode_options.index(st.session_state.control_mode.value)
    mode_label = st.radio(
        "Mod",
        mode_options,
        index=current_idx,
        key="mode_radio",
        label_visibility="collapsed",
    )
    st.session_state.control_mode = ControlMode(mode_label)
    # Show AI panel cues in Co-Pilot and Manual (admin rem/lucid), like Android.
    st.session_state.copilot_enabled = st.session_state.control_mode in (
        ControlMode.COPILOT,
        ControlMode.MANUAL,
        ControlMode.AUTO,
    )

    if role is UserRole.ADMIN:
        st.caption("Admin — Manual / Auto / AI Co-Pilot")
    elif role is UserRole.PRESENTER:
        st.caption("Sunum — tüm modlar")
    else:
        st.caption("Personel — Auto / AI Co-Pilot")

    st.markdown("---")
    st.markdown("**Mobil kullanıcı nabız (Supabase)**")
    st.caption(cloud_config_status())
    if st.button("Buluttan yenile", use_container_width=True):
        st.session_state["_cloud_vitals_cache"] = fetch_latest_vitals()
    cloud_rows = st.session_state.get("_cloud_vitals_cache")
    if cloud_rows is None:
        st.session_state["_cloud_vitals_cache"] = fetch_latest_vitals()
        cloud_rows = st.session_state["_cloud_vitals_cache"]
    if cloud_rows:
        for cv in cloud_rows[:3]:
            bpm_txt = f"{cv.bpm:.0f}" if cv.bpm is not None else "—"
            spo2_txt = f" · SpO₂ {cv.spo2:.0f}%" if cv.spo2 is not None else ""
            st.markdown(f"**{bpm_txt} BPM**{spo2_txt}")
            st.caption(f"{cv.availability} · {cv.measured_at or '—'}")
    else:
        st.caption("Kayıt yok veya RLS (anon) okuyamıyor. Consumer uygulaması ölçüm yapsın.")

    st.markdown("---")
    st.markdown("**Serial / Arduino**")
    available_ports = SerialTrigger.list_available_ports()
    port_options = ["— Select port —"] + available_ports
    selected = st.selectbox("COM Port", port_options, key="port_select")
    st.session_state.selected_port = None if selected == "— Select port —" else selected

    c1, c2 = st.columns(2)
    with c1:
        if st.button("Connect", use_container_width=True):
            if st.session_state.selected_port:
                ok = st.session_state.serial_trigger.connect(st.session_state.selected_port)
                st.success("Bağlandı") if ok else st.error(st.session_state.serial_trigger.status_message())
            else:
                st.warning("Port seçin.")
    with c2:
        if st.button("Disconnect", use_container_width=True):
            st.session_state.serial_trigger.disconnect()

    st.caption(st.session_state.serial_trigger.status_message(st.session_state.selected_port))

    st.markdown("---")

    if can_access_manual_controls(role) and st.session_state.control_mode is ControlMode.MANUAL:
        st.markdown("**Manuel Kontroller**")

        stage_names = [s.value for s in SleepStage]
        manual_idx = stage_names.index(st.session_state.manual_stage.value)
        manual_pick = st.selectbox("Manuel Aşama", stage_names, index=manual_idx)
        st.session_state.manual_stage = SleepStage(manual_pick)

        mc1, mc2 = st.columns(2)
        with mc1:
            if st.button("40Hz Tetikle", use_container_width=True):
                if _do_trigger_40hz():
                    st.success("40Hz · Lucid protokolü")
                else:
                    st.warning("Cihaz bağlı değil veya hata.")
        with mc2:
            if st.button("Durdur", use_container_width=True):
                _do_stop_stim()
                st.info("Stimülasyon durduruldu")

        if st.button("UYANDIR", use_container_width=True, type="primary"):
            _do_wake_up()
            st.success("Uyandırma protokolü gönderildi")
    else:
        st.markdown("**Otomatik Kontrol**")
        st.session_state.auto_trigger_enabled = st.toggle(
            "Otomatik REM Tetikleme",
            value=st.session_state.auto_trigger_enabled,
        )

    st.markdown("---")
    st.markdown("**Ses / Voice**")
    st.session_state.local_voice_enabled = st.toggle("Yerel Ses (PC)", value=st.session_state.local_voice_enabled)
    st.session_state.device_voice_enabled = st.toggle("Cihaz Sesi (Arduino)", value=st.session_state.device_voice_enabled)

    vol = st.slider("Ses Seviyesi", 0.0, 1.0, float(st.session_state.voice_engine.volume), 0.05)
    st.session_state.voice_engine.volume = vol
    rate = st.slider("Konuşma Hızı", 100, 220, int(st.session_state.voice_engine.rate), 5)
    st.session_state.voice_engine.rate = rate

    st.session_state.voice_message = st.text_input("Cihaza Gönderilecek Mesaj", st.session_state.voice_message)

    vc1, vc2 = st.columns(2)
    with vc1:
        if st.button("Ses Test", use_container_width=True):
            _speak_local("unity-X ses testi.")
    with vc2:
        if st.button("Cihaza Ses", use_container_width=True):
            trigger = st.session_state.serial_trigger
            if st.session_state.device_voice_enabled:
                trigger.send_command(VOICE_ON_COMMAND)
            if _send_device_voice(st.session_state.voice_message):
                st.success("Cihaza gönderildi")
            else:
                st.warning("Bağlantı yok veya ses kapalı")

    st.caption(st.session_state.voice_engine.status_message())

    st.markdown("---")
    st.markdown("**Oturum**")
    st.caption(f"EEG: {SAMPLE_RATE} Hz · Pencere: {WINDOW_SECONDS}s")

    if st.button("Start Session", use_container_width=True):
        st.session_state.session_active = True
        st.session_state.eeg_generator.reset()
        st.session_state.pulse_generator.reset()
        st.session_state.detector.reset()
        st.session_state.copilot.reset()
        st.session_state.eeg_buffer = np.array([], dtype=np.float64)
        st.session_state.time_buffer = np.array([], dtype=np.float64)
        st.session_state.pulse_buffer = np.array([], dtype=np.float64)
        st.session_state.pulse_time_buffer = np.array([], dtype=np.float64)
        st.session_state.detected_stage = SleepStage.AWAKE
        st.session_state.display_stage = SleepStage.AWAKE
        st.session_state.stim_active = False
        st.session_state.copilot_messages = []

    if st.button("Stop Session", use_container_width=True):
        st.session_state.session_active = False
        _do_stop_stim()
        st.session_state.serial_trigger.disconnect()

serial_msg = st.session_state.serial_trigger.status_message(st.session_state.selected_port)

# ---------------------------------------------------------------------------
# Main dashboard
# ---------------------------------------------------------------------------
st.markdown("# UNITY-X")
st.markdown(
    f"*Kullanıcı: {st.session_state.user_name} · Mod: {st.session_state.control_mode.value} · REM · Nabız · Ses*"
)

top1, top2 = st.columns([2, 1])
status_placeholder = top1.empty()
pulse_placeholder = top2.empty()
serial_placeholder = st.empty()
chart_row = st.columns(2)
eeg_chart_ph = chart_row[0].empty()
pulse_chart_ph = chart_row[1].empty()
copilot_ph = st.empty()

if not st.session_state.session_active:
    status_placeholder.markdown(
        '<div class="status-banner">Status: Idle — Start Session</div>',
        unsafe_allow_html=True,
    )
    pulse_placeholder.markdown(
        '<div class="pulse-box"><div class="pulse-label">NABIZ</div>— BPM</div>',
        unsafe_allow_html=True,
    )
    serial_placeholder.markdown(f'<div class="serial-status">Serial: {serial_msg}</div>', unsafe_allow_html=True)
    eeg_chart_ph.markdown('<p style="color:#666;font-family:monospace;">EEG bekleniyor…</p>', unsafe_allow_html=True)
    pulse_chart_ph.markdown('<p style="color:#666;font-family:monospace;">Nabız bekleniyor…</p>', unsafe_allow_html=True)
else:

    @st.fragment(run_every=timedelta(milliseconds=250))
    def _live_session() -> None:
        gen: MockEEGGenerator = st.session_state.eeg_generator
        pulse_gen: MockPulseGenerator = st.session_state.pulse_generator
        detector: REMSleepDetector = st.session_state.detector
        trigger: SerialTrigger = st.session_state.serial_trigger
        copilot: AICopilot = st.session_state.copilot
        mode: ControlMode = st.session_state.control_mode

        chunk = gen.next_chunk()
        chunk_times = chunk.timestamp + np.arange(len(chunk.samples)) / SAMPLE_RATE
        st.session_state.eeg_buffer = np.concatenate([st.session_state.eeg_buffer, chunk.samples])
        st.session_state.time_buffer = np.concatenate([st.session_state.time_buffer, chunk_times])
        st.session_state.eeg_buffer, st.session_state.time_buffer = _trim_buffer(
            st.session_state.eeg_buffer, st.session_state.time_buffer, CHART_SECONDS
        )

        analysis_samples, _ = _trim_buffer(
            st.session_state.eeg_buffer, st.session_state.time_buffer, WINDOW_SECONDS
        )
        result = detector.update(analysis_samples)
        st.session_state.detected_stage = result.detected_stage

        if mode is ControlMode.MANUAL:
            st.session_state.display_stage = st.session_state.manual_stage
            pulse_gen.set_stage(st.session_state.manual_stage)
        else:
            st.session_state.display_stage = result.detected_stage
            pulse_gen.set_stage(result.detected_stage)

        pulse = pulse_gen.next_reading()
        st.session_state.current_bpm = pulse.bpm
        st.session_state.pulse_buffer = np.concatenate([st.session_state.pulse_buffer, [pulse.waveform_sample]])
        st.session_state.pulse_time_buffer = np.concatenate([st.session_state.pulse_time_buffer, [pulse.timestamp]])
        st.session_state.pulse_buffer, st.session_state.pulse_time_buffer = _trim_buffer(
            st.session_state.pulse_buffer, st.session_state.pulse_time_buffer, CHART_SECONDS
        )

        now = time.monotonic()
        cooldown_ok = (now - st.session_state.last_trigger_time) >= TRIGGER_COOLDOWN_SEC
        auto_ok = (
            mode is not ControlMode.MANUAL
            and st.session_state.auto_trigger_enabled
            and result.is_rem
            and cooldown_ok
        )

        if auto_ok:
            _do_trigger_40hz()

        msgs = copilot.analyze(
            mode=mode,
            stage=st.session_state.display_stage,
            bpm=pulse.bpm,
            serial_connected=trigger.is_connected,
            stim_active=st.session_state.stim_active,
            auto_trigger_enabled=st.session_state.auto_trigger_enabled,
            device_voice_enabled=st.session_state.device_voice_enabled,
            confidence=result.confidence,
            is_rem_edge=result.is_rem,
        )
        if st.session_state.copilot_enabled:
            st.session_state.copilot_messages = msgs

        stage = st.session_state.display_stage
        status_placeholder.markdown(
            f'<div class="status-banner">{_status_label(stage, st.session_state.stim_active)}</div>',
            unsafe_allow_html=True,
        )
        pulse_placeholder.markdown(
            f'<div class="pulse-box"><div class="pulse-label">NABIZ</div>{pulse.bpm:.0f} BPM</div>',
            unsafe_allow_html=True,
        )

        note = ""
        if st.session_state.stim_active:
            note = " · 40Hz aktif"
        elif stage is SleepStage.REM and not trigger.is_connected:
            note = " · Cihaz bağlı değil"
        serial_placeholder.markdown(
            f'<div class="serial-status">Serial: {trigger.status_message(st.session_state.selected_port)}{note} · Mod: {mode.value}</div>',
            unsafe_allow_html=True,
        )

        if len(st.session_state.time_buffer) > 1:
            eeg_chart_ph.plotly_chart(
                _build_line_chart(st.session_state.time_buffer, st.session_state.eeg_buffer, "EEG (µV)"),
                use_container_width=True,
            )
        if len(st.session_state.pulse_time_buffer) > 1:
            pulse_chart_ph.plotly_chart(
                _build_line_chart(
                    st.session_state.pulse_time_buffer,
                    st.session_state.pulse_buffer,
                    "Nabız Dalga",
                    height=320,
                ),
                use_container_width=True,
            )

        if st.session_state.copilot_enabled and st.session_state.copilot_messages:
            lines = "".join(
                f'<div class="copilot-line">[{m.priority.upper()}] {m.text}</div>'
                for m in reversed(st.session_state.copilot_messages[-6:])
            )
            copilot_ph.markdown(
                f'<div class="copilot-box"><strong>AI CO-PILOT</strong>{lines}</div>',
                unsafe_allow_html=True,
            )

    _live_session()
