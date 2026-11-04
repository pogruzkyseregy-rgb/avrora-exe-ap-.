@echo off
chcp 65001 >nul
echo Ставлю edl (bkerler) и pyusb...
py -m pip install --upgrade pip
py -m pip install pyusb https://github.com/bkerler/edl/archive/refs/heads/master.zip
echo.
echo Готово. Дальше: драйвер через Zadig (см. README), потом:  py stand_agent.py
pause
