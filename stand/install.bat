@echo off
cd /d "%~dp0"
if not exist stand_agent.py (
  echo ERROR: run install.bat from the stand folder, next to stand_agent.py.
  pause
  exit /b 1
)
call findpy.cmd
if not defined PY (
  echo ERROR: Python not found. Install it from python.org: tick "Add python.exe to PATH", then Install Now.
  pause
  exit /b 1
)
echo Python: %PY%
echo [1/2] Installing edl...
%PY% -m pip install --upgrade pip
%PY% -m pip install pyusb pyserial docopt pycryptodome pycryptodomex lxml colorama requests
%PY% -m pip install --no-deps https://github.com/bkerler/edl/archive/refs/heads/master.zip
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
echo.
if exist platform-tools\fastboot.exe (echo OK: platform-tools ready) else (echo ERROR: platform-tools not unpacked)
%PY% -c "import usb, edlclient.Library.utils; print(\"OK: edl ready\")"
echo Next: Zadig driver for EDL, then run start.bat
pause
