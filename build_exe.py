"""
Build unity-X as a standalone Windows .exe (optional).

Usage:
    pip install pyinstaller
    python build_exe.py
"""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
DIST_DIR = ROOT / "dist"
BUILD_DIR = ROOT / "build"


def main() -> None:
    try:
        import PyInstaller  # noqa: F401
    except ImportError:
        print("PyInstaller required: pip install pyinstaller")
        raise SystemExit(1)

    for folder in (DIST_DIR, BUILD_DIR):
        if folder.exists():
            shutil.rmtree(folder)

    cmd = [
        sys.executable,
        "-m",
        "PyInstaller",
        "--noconfirm",
        "--onefile",
        "--windowed",
        "--name",
        "unity-X",
        "--add-data",
        f"{ROOT / 'app.py'};.",
        "--add-data",
        f"{ROOT / 'nexus_neuro'};nexus_neuro",
        "--add-data",
        f"{ROOT / '.streamlit'};.streamlit",
        "--hidden-import",
        "streamlit",
        "--hidden-import",
        "plotly",
        "--hidden-import",
        "serial",
        "--hidden-import",
        "webview",
        "--collect-all",
        "streamlit",
        "--collect-all",
        "plotly",
        str(ROOT / "main.py"),
    ]

    print("Building unity-X.exe …")
    subprocess.check_call(cmd, cwd=str(ROOT))
    exe_path = DIST_DIR / "unity-X.exe"
    print(f"\nDone: {exe_path}")
    print("Copy the .exe anywhere and double-click to run.")


if __name__ == "__main__":
    main()
