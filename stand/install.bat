@echo off
cd /d "%~dp0"
if not exist stand_agent.py (
  echo ERROR: put install.bat into the stand folder, next to stand_agent.py, and run it from there.
  pause
  exit /b 1
)
where py >nul 2>nul
if errorlevel 1 (
  echo ERROR: Python not found. Install it from python.org and tick "Add python.exe to PATH".
  pause
  exit /b 1
)
echo [1/2] Installing edl and pyusb...
py -m pip install --upgrade pip
py -m pip install pyusb https://github.com/bkerler/edl/archive/refs/heads/master.zip
echo [2/2] Android platform-tools - fastboot and adb...
if exist platform-tools\fastboot.exe goto ptok
if exist platform-tools-latest-windows.zip goto ptunzip
powershell -NoProfile -Command "Invoke-WebRequest https://dl.google.com/android/repository/platform-tools-latest-windows.zip -OutFile platform-tools-latest-windows.zip"
if not exist platform-tools-latest-windows.zip (
  echo ERROR: dl.google.com is not reachable. Turn on VPN and run again,
  echo or download platform-tools-latest-windows.zip in the browser and put it into this folder.
  pause
  exit /b 1
)
:ptunzip
powershell -NoProfile -Command "Expand-Archive -Force platform-tools-latest-windows.zip ."
:ptok
if exist platform-tools\fastboot.exe (echo OK: platform-tools ready) else (echo ERROR: platform-tools not unpacked)
py -c "import usb, edlclient; print(\"OK: edl and pyusb ready\")"
echo Next: Zadig driver, then run:  py stand_agent.py
pause
