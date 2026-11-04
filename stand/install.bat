@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo [1/2] edl (bkerler) и pyusb...
py -m pip install --upgrade pip
py -m pip install pyusb https://github.com/bkerler/edl/archive/refs/heads/master.zip
echo [2/2] Android platform-tools (fastboot, adb)...
powershell -NoProfile -Command "Invoke-WebRequest https://dl.google.com/android/repository/platform-tools-latest-windows.zip -OutFile pt.zip; Expand-Archive -Force pt.zip .; Remove-Item pt.zip"
echo.
echo Готово. Дальше: драйвер через Zadig (см. README), потом:  py stand_agent.py
pause
