@echo off
title Nexus Neuro
cd /d "%~dp0"

REM Prefer pythonw (no console window). Fall back to python if unavailable.
where pythonw >nul 2>&1
if %ERRORLEVEL%==0 (
    start "" pythonw "%~dp0main.py"
) else (
    python "%~dp0main.py"
)
