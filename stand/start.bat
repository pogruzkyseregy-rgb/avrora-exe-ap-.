@echo off
cd /d "%~dp0"
call findpy.cmd
if not defined PY (
  echo ERROR: Python not found. Run install.bat first.
  pause
  exit /b 1
)
%PY% stand_agent.py
pause
