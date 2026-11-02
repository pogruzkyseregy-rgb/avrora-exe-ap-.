#!/usr/bin/env bash
# Поднять ОСНОВНОЙ сервер из копии на чистом Ubuntu (22.04/24.04). Запуск от root на НОВОМ сервере:
#   bash restore-main.sh /root/avrora-main-ДАТА.tar.gz
# Подходит и старая копия aurora-backup.tar.gz. Окружения Python собираются заново под этот сервер.
# DRY=1 — без apt/systemctl (проверка), ROOT=/путь — распаковать не в корень.
set -euo pipefail
ARCH="${1:?Укажи архив: bash restore-main.sh /root/avrora-main-….tar.gz}"
[ -f "$ARCH" ] || { echo "Нет файла $ARCH"; exit 1; }
R="${ROOT:-}"; DRY="${DRY:-0}"
run() { if [ "$DRY" = 1 ]; then echo "  (пропущено) $*"; else "$@"; fi; }
[ "$DRY" = 1 ] || [ "$(id -u)" = 0 ] || { echo "Нужен root."; exit 1; }

echo "[1/7] Пакеты"
export DEBIAN_FRONTEND=noninteractive
run apt-get update -qq
run apt-get install -y -qq nginx certbot python3-certbot-nginx python3-venv python3-pip ffmpeg abcmidi fluidsynth \
    fluid-soundfont-gm sqlite3 curl openssl openssh-client

echo "[2/7] Файлы из копии"
mkdir -p "${R:-/}"
tar xzf "$ARCH" -C "${R:-/}" --exclude='*/venv' --exclude='*/venv/*' --exclude='*/__pycache__'
chmod 600 "$R/opt/aurora-relay/relay.env" "$R/opt/aurora-relay/hand_key" 2>/dev/null || true
[ -f "$R/opt/avrora-admin/.env" ] && chmod 600 "$R/opt/avrora-admin/.env"

# список пакетов: requirements.lock из новой копии, иначе — по содержимому старого окружения в архиве, иначе — базовый
reqs() {  # $1 = aurora-relay | avrora-admin
  local f="$R/opt/$1/requirements.lock"
  if [ -s "$f" ]; then cat "$f"; return; fi
  local got
  got="$(tar tzf "$ARCH" 2>/dev/null | grep -E "^opt/$1/venv/lib/python[0-9.]+/site-packages/[^/]+\.dist-info/$" \
        | sed -E 's#.*/site-packages/([^/]+)\.dist-info/#\1#' | sed -E 's/^(.+)-([0-9][^-]*)$/\1==\2/' \
        | grep -viE '^(pip|setuptools|wheel)==' || true)"
  if [ -n "$got" ]; then echo "$got"; return; fi
  if [ "$1" = aurora-relay ]; then printf 'fastapi\nuvicorn\nhttpx\npydantic\npillow\n'; else printf 'flask\ngunicorn\npillow\n'; fi
}

echo "[3/7] Окружения Python"
for app in aurora-relay avrora-admin; do
  [ -d "$R/opt/$app" ] || continue
  reqs "$app" > "$R/opt/$app/requirements.restore"
  echo "  $app: $(wc -l < "$R/opt/$app/requirements.restore") пакетов"
  if [ "$DRY" != 1 ]; then
    python3 -m venv "/opt/$app/venv"
    "/opt/$app/venv/bin/pip" install -q --upgrade pip
    # точные версии; не встали (другой Python) — те же пакеты без версий
    "/opt/$app/venv/bin/pip" install -q -r "/opt/$app/requirements.restore" \
      || "/opt/$app/venv/bin/pip" install -q $(sed -E 's/[=<>].*//' "/opt/$app/requirements.restore")
  fi
done

echo "[4/7] Службы"
U="$R/etc/systemd/system"; mkdir -p "$U"
if [ ! -f "$U/aurora-relay.service" ]; then
  cat > "$U/aurora-relay.service" <<'UNIT'
[Unit]
Description=Aurora app relay
After=network.target

[Service]
WorkingDirectory=/opt/aurora-relay
EnvironmentFile=/opt/aurora-relay/relay.env
ExecStart=/opt/aurora-relay/venv/bin/uvicorn relay:app --host 127.0.0.1 --port 8765
Restart=always

[Install]
WantedBy=multi-user.target
UNIT
fi
ADMIN_UNIT="$(grep -l 'avrora-admin' "$U"/*.service 2>/dev/null | grep -v aurora-relay | head -1 || true)"
if [ -z "$ADMIN_UNIT" ] && [ -f "$R/opt/avrora-admin/app.py" ]; then
  ADMIN_UNIT="$U/avrora-admin.service"
  cat > "$ADMIN_UNIT" <<'UNIT'
[Unit]
Description=avrora.exe site admin
After=network.target

[Service]
WorkingDirectory=/opt/avrora-admin
EnvironmentFile=/opt/avrora-admin/.env
ExecStart=/opt/avrora-admin/venv/bin/gunicorn --bind 127.0.0.1:8090 --workers 1 --threads 8 --timeout 600 app:app
Restart=always

[Install]
WantedBy=multi-user.target
UNIT
  echo "  служба админки создана заново: $(basename "$ADMIN_UNIT")"
fi

echo "[5/7] nginx"
mkdir -p "$R/etc/nginx/sites-enabled"
ln -sf /etc/nginx/sites-available/avrora "$R/etc/nginx/sites-enabled/avrora"
rm -f "$R/etc/nginx/sites-enabled/default"
run nginx -t
run systemctl reload nginx

echo "[6/7] Запуск"
run systemctl daemon-reload
run systemctl enable --now aurora-relay
[ -n "$ADMIN_UNIT" ] && run systemctl enable --now "$(basename "$ADMIN_UNIT")"
if [ "$DRY" != 1 ]; then sleep 3; echo "  сервер девчонок: $(curl -s http://127.0.0.1:8765/health || echo 'не отвечает')"; fi

echo "[7/7] Что осталось сделать руками"
IP="$(ip route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}' || true)"
echo "  1) В SpaceWeb (DNS домена avrora-exe.ru) поменяй A-запись на ${IP:-адрес этого сервера} — для avrora-exe.ru и www."
echo "  2) Когда домен заработает на новом адресе:  certbot renew --dry-run"
echo "     Если сертификат не продлевается:  certbot --nginx -d avrora-exe.ru -d www.avrora-exe.ru"
echo "  3) Если машина руки тоже переехала:  bash set-hand.sh НОВЫЙ_IP   (или 127.0.0.1, если рука на этом же сервере)"
echo "  4) Приложение менять не нужно: адрес https://avrora-exe.ru/app-api и токен те же."
