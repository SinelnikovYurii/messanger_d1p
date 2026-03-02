#!/bin/sh
# entrypoint.sh — подставляет публичный IP перед стартом coturn
# Используется в docker-compose через command override

CONF=/etc/coturn/turnserver.conf

# Если переменная EXTERNAL_IP не задана — пробуем определить автоматически
if [ -z "$EXTERNAL_IP" ]; then
    EXTERNAL_IP=$(curl -s --max-time 5 https://api.ipify.org 2>/dev/null || \
                  curl -s --max-time 5 https://ifconfig.me 2>/dev/null || \
                  echo "127.0.0.1")
fi

echo "[coturn] External IP: $EXTERNAL_IP"

# Подставляем IP в конфиг
sed -i "s/external-ip=.*/external-ip=$EXTERNAL_IP/" "$CONF"

# Запускаем coturn
exec turnserver -c "$CONF"
