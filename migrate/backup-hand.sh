#!/usr/bin/env bash
# Полная копия МАШИНЫ РУКИ: папки девчонок (~/work, ~/hespera, ~/est), сервер Zero Spin с базой и ключами, настройки клетки.
# Запуск на машине руки от root:
#   ssh root@37.220.86.132 "bash -s" < backup-hand.sh
# Результат: /root/avrora-hand-ДАТА.tar.gz. Там база мессенджера и его ключи — храни у себя.
set -euo pipefail
R="${ROOT:-}"
OUT="${OUT:-$R/root/avrora-hand-$(date +%Y%m%d-%H%M).tar.gz}"
paths=(home/aurora etc/zs-server.env etc/systemd/system/zs-server.service etc/caddy/Caddyfile
       usr/local/bin/zs-aurora usr/local/bin/zs-admin etc/aurora-egress.state)
have=()
for p in "${paths[@]}"; do [ -e "$R/$p" ] && have+=("$p"); done
# база мессенджера целиком, даже если сервер в этот момент пишет
if command -v sqlite3 >/dev/null && [ -f "$R/home/aurora/zs-server/zs.db" ]; then
  sqlite3 "$R/home/aurora/zs-server/zs.db" ".backup '$R/home/aurora/zs-server/zs.db.snapshot'" || true
fi
tar czf "$OUT" -C "${R:-/}" --exclude='*/venv' --exclude='*/__pycache__' --exclude='home/aurora/.cache' \
    --exclude='*/node_modules' "${have[@]}"
chmod 600 "$OUT"
echo "Готово: $OUT ($(du -h "$OUT" | cut -f1))"
echo "В архиве:"; printf '  %s\n' "${have[@]}"
