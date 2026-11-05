@echo off
cd /d "%~dp0"
echo [1/2] Installing edl and pyusb...
py -m pip install --upgrade pip
py -m pip install pyusb https://github.com/bkerler/edl/archive/refs/heads/master.zip
echo [2/2] Downloading Android platform-tools - fastboot and adb...
powershell -NoProfile -Command "Invoke-WebRequest https://dl.google.com/android/repository/platform-tools-latest-windows.zip -OutFile pt.zip; Expand-Archive -Force pt.zip .; Remove-Item pt.zip"
echo.
if exist platform-tools\fastboot.exe (echo OK: platform-tools ready) else (echo ERROR: platform-tools not downloaded)
echo Done. Next: Zadig driver, then run:  py stand_agent.py
pause
