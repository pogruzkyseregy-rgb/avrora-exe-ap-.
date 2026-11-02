#!/usr/bin/env bash
# Поднять МАШИНУ РУКИ из копии на чистом Ubuntu. Запуск от root на НОВОЙ машине, из папки распакованного архива проекта
# (нужны соседние hand/setup_hand.sh и hand/widen_hand.sh):
#   bash migrate/restore-hand.sh /root/avrora-hand-ДАТА.tar.gz
# Если рука переезжает на ОСНОВНОЙ сервер (одна машина на всё): COMBINED=1 bash migrate/restore-hand.sh …
#   тогда HTTPS для Zero Spin делает nginx, а не Caddy (оба на один порт 443 не встанут).
# WIDE=0 — не выдавать Авроре sudo (по умолчанию выдаётся, как было).
set -euo pipefail
ARCH="${1:?Укажи архив: bash migrate/restore-hand.sh /root/avrora-hand-….tar.gz}"
[ -f "$ARCH" ] || { echo "Нет файла $ARCH"; exit 1; }
[ "$(id -u)" = 0 ] || { echo "Нужен root."; exit 1; }
HERE="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$HERE/hand/setup_hand.sh" ] || { echo "Рядом нет hand/setup_hand.sh — запусти из папки распакованного архива проекта."; exit 1; }
WIDE="${WIDE:-1}"; COMBINED="${COMBINED:-0}"

echo "[1/6] Ключ сервера девчонок из копии"
KEY="${AURORA_PUBKEY:-$(tar xzOf "$ARCH" home/aurora/.ssh/authorized_keys 2>/dev/null | grep -m1 'aurora-hand' || true)}"
[ -n "$KEY" ] || KEY="$(tar xzOf "$ARCH" home/aurora/.ssh/authorized_keys 2>/dev/null | grep -m1 '^ssh-ed25519' || true)"
[ -n "$KEY" ] || { echo "В копии нет ключа. Задай: AURORA_PUBKEY=\"\$(ssh root@ОСНОВНОЙ cat /opt/aurora-relay/hand_key.pub)\""; exit 1; }

echo "[2/6] Клетка для руки (пользователь aurora, лимиты, рубильники)"
AURORA_PUBKEY="$KEY" AURORA_EGRESS=open bash "$HERE/hand/setup_hand.sh"
if [ "$WIDE" = 1 ]; then bash "$HERE/hand/widen_hand.sh"; fi

echo "[3/6] Файлы из копии: папки девчонок, Zero Spin, его база и ключи"
systemctl stop zs-server 2>/dev/null || true
tar xzf "$ARCH" -C / --exclude='*/venv' --exclude='*/__pycache__'
Z=/home/aurora/zs-server
[ -f "$Z/zs.db.snapshot" ] && mv -f "$Z/zs.db.snapshot" "$Z/zs.db"
chown -R aurora:aurora /home/aurora
[ -f /etc/zs-server.env ] && { chmod 640 /etc/zs-server.env; chgrp aurora /etc/zs-server.env; }

echo "[4/6] Zero Spin: окружение Python"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq python3-venv sqlite3 curl >/dev/null
if [ -d "$Z" ]; then
  sudo -u aurora python3 -m venv "$Z/venv"
  sudo -u aurora "$Z/venv/bin/pip" install -q --upgrade pip
  sudo -u aurora "$Z/venv/bin/pip" install -q fastapi uvicorn "httpx[http2]"
fi

echo "[5/6] HTTPS для Zero Spin"
IP="$(ip route get 1.1.1.1 | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}')"
HOST="$(echo "$IP" | tr . -).sslip.io"
if [ "$COMBINED" = 1 ]; then
  cat > /etc/nginx/sites-available/zero-spin <<CONF
server {
    listen 80;
    server_name $HOST;
    client_max_body_size 25m;
    location / {
        proxy_pass http://127.0.0.1:8077;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 600s;
    }
}
CONF
  ln -sf /etc/nginx/sites-available/zero-spin /etc/nginx/sites-enabled/zero-spin
  nginx -t && systemctl reload nginx
  certbot --nginx -d "$HOST" --non-interactive --agree-tos --register-unsafely-without-email || echo "  сертификат не выдан — повтори позже: certbot --nginx -d $HOST"
else
  apt-get install -y -qq caddy >/dev/null
  printf '%s {\n    reverse_proxy 127.0.0.1:8077\n}\n' "$HOST" > /etc/caddy/Caddyfile
  systemctl enable --now caddy >/dev/null 2>&1; systemctl reload caddy || systemctl restart caddy
fi

echo "[6/6] Запуск"
systemctl daemon-reload
[ -f /etc/systemd/system/zs-server.service ] && systemctl enable --now zs-server >/dev/null 2>&1 && systemctl restart zs-server
sleep 3
echo "  Zero Spin изнутри: $(curl -s http://127.0.0.1:8077/api/health || echo 'не отвечает')"
echo
echo "ГОТОВО. Осталось:"
echo "  1) На основном сервере:  bash set-hand.sh $IP$( [ "$COMBINED" = 1 ] && echo '   (или 127.0.0.1)')"
echo "  2) Новый адрес Zero Spin для приложения мессенджера:  https://$HOST"
[ "$WIDE" = 1 ] && [ "$COMBINED" = 1 ] && echo "  ! На общей машине у Авроры sudo — значит, ей видны и ключи основного сервера. Не хочешь — запусти: bash hand/widen_hand.sh --undo и убери HAND_SUDO=1 из relay.env."
exit 0
