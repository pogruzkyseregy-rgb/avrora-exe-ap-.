# Стенд на ПК: полный доступ к старому телефону

Старый Nothing Phone (2), подключённый к ПК, — стенд для тренировок Авроры, Гесперы и Эст.
С телефоном можно всё: EDL (чтение, запись, стирание разделов), fastboot, adb, скачивание прошивок и загрузчиков.

Границы — только у ПК, не у телефона:
- запускаются только `edl`, `fastboot`, `adb` — без командной строки Windows;
- все файлы — только в папке `стенд` рядом с агентом: туда качается, оттуда шьётся, туда читается;
- adb не открывает сеть и туннели ПК (`connect`, `forward`, `reverse`, `tcpip`, `pair`), `edl server` выключен;
- закрыл окно агента (или Ctrl+C) — доступа нет. Агент сам стучится на сервер, порты открывать не нужно.

Что могут девчонки (они пишут это сами):
| Команда | Что делает |
|---|---|
| `[стенд: usb]` | что видит ПК: EDL — `05c6:9008`, fastboot — `18d1:4ee0` |
| `[стенд: edl printgpt]`, `[стенд: edl r boot_a boot_a.img]`, `[стенд: edl w boot_a boot.img]`, `[стенд: edl e misc]` | EDL |
| `[стенд: fastboot getvar all]`, `[стенд: fastboot flash boot_a boot.img]`, `[стенд: fastboot reboot edl]` | fastboot |
| `[стенд: adb shell getprop]`, `[стенд: adb push файл /sdcard/]`, `[стенд: adb reboot edl]` | adb |
| `[стенд: файлы]` | что лежит в папке стенда |
| `[стенд: скачать https://адрес имя]` | скачать в папку стенда (до 4 ГБ) |
| `[стенд: итог №]` | итог долгой команды (прошивка идёт дольше полутора минут — вернётся номер) |

**Сейчас ничего ставить не нужно** — это заготовка. Когда дойдут руки:

## 1. Токен стенда на сервере (один раз, из Termux)
```
ssh root@92.118.112.46 'T=$(openssl rand -hex 24); sed -i "/^STAND_TOKEN=/d" /opt/aurora-relay/relay.env; echo "STAND_TOKEN=$T" >> /opt/aurora-relay/relay.env; systemctl restart aurora-relay; echo "Токен стенда: $T"'
```
Это отдельный токен, не тот, что в приложении.

## 2. ПК (Windows)
1. Python с python.org, при установке отметь «Add python.exe to PATH».
2. Положи папку `stand` куда угодно, запусти `install.bat` — поставит edl, pyusb и platform-tools (fastboot, adb).
3. Драйвер EDL: телефон в EDL по кабелю → Zadig (zadig.akeo.ie) → Options → List All Devices → `QUSB__BULK` → **WinUSB** → Replace Driver.
4. Драйвер fastboot/adb: если `fastboot devices` пуст — Google USB Driver или тот же Zadig на `Android` → WinUSB.
5. `py stand_agent.py` — создаст `stand_agent.json`. Впиши туда `token` из шага 1 и запусти снова.

В `stand_agent.json` можно поменять: `tools` (что разрешено), `timeout_min` (30 — сколько ждать одну команду), `download_max_mb`.

## 3. Проверка
Скажи любой из девчонок: «проверь стенд». Статус с телефона:
```
ssh root@92.118.112.46 'T=$(grep ^APP_TOKEN= /opt/aurora-relay/relay.env | cut -d= -f2-); curl -s -H "Authorization: Bearer $T" http://127.0.0.1:8765/stand/status; echo'
```
