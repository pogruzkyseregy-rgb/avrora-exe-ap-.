#!/usr/bin/env bash
# Сказать серверу девчонок, где теперь машина руки. Запуск на ОСНОВНОМ сервере от root:
#   bash set-hand.sh НОВЫЙ_IP        (127.0.0.1 — если рука на этой же машине)
set -euo pipefail
IP="${1:?Укажи адрес машины руки}"
ENV="${ENV_FILE:-/opt/aurora-relay/relay.env}"
sed -i '/^HAND_HOST=/d' "$ENV"; echo "HAND_HOST=$IP" >> "$ENV"
ssh-keygen -R "$IP" >/dev/null 2>&1 || true
KEY="$(grep ^HAND_KEY= "$ENV" | cut -d= -f2-)"; KEY="${KEY:-/opt/aurora-relay/hand_key}"
USER_="$(grep ^HAND_USER= "$ENV" | cut -d= -f2-)"; USER_="${USER_:-aurora}"
if ssh -i "$KEY" -o BatchMode=yes -o StrictHostKeyChecking=accept-new -o ConnectTimeout=10 "$USER_@$IP" true; then
  echo "Рука отвечает на $IP."
else
  echo "Рука на $IP не пускает ключ сервера. Проверь, что там выполнен restore-hand.sh."
fi
systemctl restart aurora-relay && echo "Сервер девчонок перезапущен."
