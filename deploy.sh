#!/bin/bash
# ============================================================
# deploy.sh — скрипт деплоя мессенджера на VPS (Ubuntu/Debian)
# Запуск: bash deploy.sh
# ============================================================
set -e

echo "=== Деплой Messenger ==="

# 1. Проверяем Docker
if ! command -v docker &> /dev/null; then
    echo "[INFO] Устанавливаем Docker..."
    curl -fsSL https://get.docker.com | sh
    sudo usermod -aG docker $USER
    echo "[OK] Docker установлен"
fi

# 2. Проверяем Docker Compose
if ! docker compose version &> /dev/null; then
    echo "[INFO] Устанавливаем Docker Compose plugin..."
    sudo apt-get update
    sudo apt-get install -y docker-compose-plugin
fi

# 3. Открываем нужные порты
echo "[INFO] Настраиваем firewall..."
sudo ufw allow 22/tcp    2>/dev/null || true
sudo ufw allow 80/tcp    2>/dev/null || true
sudo ufw allow 443/tcp   2>/dev/null || true
sudo ufw allow 3478/udp  2>/dev/null || true
sudo ufw allow 3478/tcp  2>/dev/null || true
sudo ufw allow 49200:49300/udp 2>/dev/null || true
sudo ufw --force enable  2>/dev/null || true
echo "[OK] Firewall настроен"

# 4. Создаём .env если не существует
if [ ! -f ".env" ]; then
    echo "[WARN] Файл .env не найден, создаём из шаблона..."
    cp .env .env.backup 2>/dev/null || true
    cat > .env << 'EOF'
POSTGRES_DB=messenger_db
POSTGRES_USER=postgres
POSTGRES_PASSWORD=CHANGE_ME_STRONG_PASSWORD

JWT_SECRET=CHANGE_ME_VERY_LONG_SECRET_AT_LEAST_64_CHARS_RANDOM_STRING_HERE

TURN_SECRET=CHANGE_ME_TURN_SECRET
EOF
    echo "[WARN] Отредактируйте .env и запустите скрипт снова!"
    exit 1
fi

# 5. Обновляем внешний IP в coturn/turnserver.conf
PUBLIC_IP=$(curl -s https://api.ipify.org || curl -s https://ifconfig.me || echo "127.0.0.1")
echo "[INFO] Публичный IP: $PUBLIC_IP"
sed -i "s/external-ip=.*/external-ip=$PUBLIC_IP/" coturn/turnserver.conf
echo "[OK] coturn настроен с IP: $PUBLIC_IP"

# 6. Собираем и запускаем
echo "[INFO] Собираем образы (это может занять несколько минут)..."
docker compose build --no-cache

echo "[INFO] Запускаем сервисы..."
docker compose up -d

echo "[INFO] Ждём запуска (60 сек)..."
sleep 60

# 7. Проверяем статус
echo ""
echo "=== Статус контейнеров ==="
docker compose ps

echo ""
echo "=== Готово! ==="
echo "Приложение доступно на: http://$PUBLIC_IP"
echo "Gateway API:            http://$PUBLIC_IP:8083"
echo ""
echo "Полезные команды:"
echo "  Логи всех сервисов:   docker compose logs -f"
echo "  Логи конкретного:     docker compose logs -f gateway"
echo "  Перезапуск:           docker compose restart"
echo "  Остановка:            docker compose down"
