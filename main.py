"""
unity-X — Desktop Application Launcher.

Starts the Streamlit dashboard in the background and opens it inside a
native desktop window (no browser tab required).

Run:
    python main.py

Double-click:
    unity-X.bat   (Windows)
"""

from __future__ import annotations

import os
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
import webbrowser
from pathlib import Path

# Project root — works both from source and from PyInstaller bundle.
if getattr(sys, "frozen", False):
    APP_DIR = Path(sys._MEIPASS)  # type: ignore[attr-defined]
    EXE_DIR = Path(sys.executable).parent
else:
    APP_DIR = Path(__file__).resolve().parent
    EXE_DIR = APP_DIR

APP_FILE = APP_DIR / "app.py"
WINDOW_TITLE = "unity-X"
WINDOW_WIDTH = 1280
WINDOW_HEIGHT = 820
STARTUP_TIMEOUT_SEC = 45


def _find_free_port() -> int:
    """Pick an available localhost port for the embedded Streamlit server."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def _wait_for_server(url: str, timeout_sec: float = STARTUP_TIMEOUT_SEC) -> None:
    """Poll until Streamlit responds or timeout."""
    deadline = time.monotonic() + timeout_sec
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(url, timeout=1) as response:
                if response.status == 200:
                    return
        except (urllib.error.URLError, TimeoutError, ConnectionError):
            time.sleep(0.25)
    raise RuntimeError(f"Dashboard did not start within {timeout_sec:.0f} seconds.")


def _show_error(message: str) -> None:
    """Display a native error dialog on Windows/macOS/Linux."""
    try:
        import tkinter as tk
        from tkinter import messagebox

        root = tk.Tk()
        root.withdraw()
        messagebox.showerror(WINDOW_TITLE, message)
        root.destroy()
    except Exception:
        print(message, file=sys.stderr)


def _start_streamlit(port: int) -> subprocess.Popen[str]:
    """Launch Streamlit as a background subprocess."""
    if not APP_FILE.exists():
        raise FileNotFoundError(f"Dashboard file not found: {APP_FILE}")

    env = os.environ.copy()
    env["STREAMLIT_SERVER_PORT"] = str(port)
    env["STREAMLIT_BROWSER_GATHER_USAGE_STATS"] = "false"
    env["PYTHONPATH"] = os.pathsep.join(
        [str(APP_DIR), env.get("PYTHONPATH", "")]
    ).strip(os.pathsep)

    cmd = [
        sys.executable,
        "-m",
        "streamlit",
        "run",
        str(APP_FILE),
        "--server.headless",
        "true",
        "--server.port",
        str(port),
        "--browser.gatherUsageStats",
        "false",
        "--global.developmentMode",
        "false",
    ]

    # Hide console window on Windows when using pythonw / frozen exe.
    creationflags = 0
    if sys.platform == "win32":
        creationflags = getattr(subprocess, "CREATE_NO_WINDOW", 0)

    return subprocess.Popen(
        cmd,
        cwd=str(APP_DIR),
        env=env,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        creationflags=creationflags,
    )


def _open_native_window(url: str) -> None:
    """Open the dashboard inside a native desktop window."""
    import webview

    window = webview.create_window(
        title=WINDOW_TITLE,
        url=url,
        width=WINDOW_WIDTH,
        height=WINDOW_HEIGHT,
        resizable=True,
        background_color="#000000",
        text_select=True,
    )
    webview.start(gui="edgechromium" if sys.platform == "win32" else None)


def _open_browser_fallback(url: str) -> None:
    """Open the dashboard in the default browser as a compatibility fallback."""
    if not webbrowser.open(url):
        raise RuntimeError("Could not open the dashboard URL in the default browser.")


def main() -> int:
    """Entry point for the unity-X desktop application."""
    port = _find_free_port()
    url = f"http://127.0.0.1:{port}"
    server: subprocess.Popen[str] | None = None

    try:
        server = _start_streamlit(port)
        _wait_for_server(url)
        try:
            _open_native_window(url)
        except Exception as webview_exc:
            # Some Windows setups (notably newer Python versions) can fail to load
            # the pywebview .NET backend. Fallback to browser mode instead of exiting.
            _show_error(
                "Desktop window could not be started.\n\n"
                "Falling back to browser mode.\n\n"
                f"Details: {webview_exc}"
            )
            _open_browser_fallback(url)
            if server.poll() is None:
                server.wait()
        return 0
    except Exception as exc:
        _show_error(f"Could not start unity-X.\n\n{exc}")
        return 1
    finally:
        if server is not None and server.poll() is None:
            server.terminate()
            try:
                server.wait(timeout=5)
            except subprocess.TimeoutExpired:
                server.kill()


if __name__ == "__main__":
    raise SystemExit(main())
