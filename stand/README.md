# Стенд EDL на ПК (этап 1: только чтение)

Старый Nothing Phone (2), подключённый к ПК, становится стендом для тренировок девчонок с EDL.
Агент сам стучится на сервер, открывать порты не нужно. Разрешено только: `usb`, `printgpt`, `getstorageinfo`.
Закрыл окно агента — доступа нет.

## 1. Токен стенда на сервере (один раз, с телефона в Termux)
```
ssh root@92.118.112.46 'T=$(openssl rand -hex 24); sed -i "/^STAND_TOKEN=/d" /opt/aurora-relay/relay.env; echo "STAND_TOKEN=$T" >> /opt/aurora-relay/relay.env; systemctl restart aurora-relay; echo "Токен стенда: $T"'
```
Это отдельный токен, не тот, что в приложении.

## 2. ПК (Windows)
1. Python с python.org, при установке отметь «Add python.exe to PATH».
2. Положи эту папку `stand` куда угодно, запусти `install.bat`.
3. Переведи телефон в EDL и подключи кабелем. В «Диспетчере устройств» появится `QUSB__BULK` или `Qualcomm HS-USB QDLoader 9008`.
4. Драйвер: скачай Zadig (zadig.akeo.ie) → Options → List All Devices → выбери `QUSB__BULK` → драйвер **WinUSB** → Replace Driver.
5. `py stand_agent.py` — создаст `stand_agent.json`. Впиши туда `token` из шага 1 и запусти снова.
   Если есть загрузчик (firehose) под этот телефон — путь к нему в `loader`.

## 3. Проверка
Скажи кому-нибудь из девчонок: «проверь стенд». Она поставит `[стенд: usb]` — в ответе должна быть строка
`05c6:9008  ← телефон в EDL`. Статус с телефона:
```
ssh root@92.118.112.46 'T=$(grep ^APP_TOKEN= /opt/aurora-relay/relay.env | cut -d= -f2-); curl -s -H "Authorization: Bearer $T" http://127.0.0.1:8765/stand/status; echo'
```
