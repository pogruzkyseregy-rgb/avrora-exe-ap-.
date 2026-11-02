#!/usr/bin/env bash
# Полная копия ОСНОВНОГО сервера (сайт, админка, сервер девчонок, их память, сертификаты, ключ подписи).
# Запуск на основном сервере от root:
#   ssh root@92.118.112.46 "bash -s" < backup-main.sh
# Результат: /root/avrora-main-ДАТА.tar.gz. В нём ключи API и токены — храни у себя, в публичный GitHub не клади.
# ROOT=/путь — для проверки на копии файлов (по умолчанию корень системы).
set -euo pipefail
R="${ROOT:-}"
OUT="${OUT:-$R/root/avrora-main-$(date +%Y%m%d-%H%M).tar.gz}"

# список пакетов Python рядом с кодом: на новом сервере окружение соберётся заново под его версию Python
for app in aurora-relay avrora-admin; do
  if [ -x "$R/opt/$app/venv/bin/pip" ]; then
    "$R/opt/$app/venv/bin/pip" freeze 2>/dev/null | grep -v '^pkg[-_]resources' > "$R/opt/$app/requirements.lock" || true
  fi
done

paths=(opt/aurora-relay opt/avrora-admin var/www/avrora
       etc/nginx/sites-available/avrora etc/nginx/conf.d/aurora-upload.conf etc/letsencrypt
       etc/systemd/system/aurora-relay.service)
# служба админки называется как угодно — берём все, где упоминается avrora-admin
for u in "$R"/etc/systemd/system/*.service; do
  [ -f "$u" ] && grep -q 'avrora-admin' "$u" && paths+=("${u#$R/}")
done
for k in "$R"/root/*.jks; do [ -f "$k" ] && paths+=("${k#$R/}"); done
[ -f "$R/var/spool/cron/crontabs/root" ] && paths+=(var/spool/cron/crontabs/root)

have=()
for p in "${paths[@]}"; do [ -e "$R/$p" ] && have+=("$p"); done
tar czf "$OUT" -C "${R:-/}" --exclude='*/venv' --exclude='*/__pycache__' "${have[@]}"
chmod 600 "$OUT"
echo "Готово: $OUT ($(du -h "$OUT" | cut -f1))"
echo "В архиве:"; printf '  %s\n' "${have[@]}"
