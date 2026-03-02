# Деплой на VPS

## Что было сделано

Каждый сервис теперь запускается в **отдельном Docker-контейнере**:

| Контейнер | Сервис | Порт |
|---|---|---|
| `messenger_postgres` | PostgreSQL | 5432 |
| `messenger_kafka` | Kafka (KRaft) | 9092 |
| `messenger_coturn` | TURN/STUN | 3478 |
| `messenger_auth` | Authorization Service | 8081 (внутренний) |
| `messenger_core_api` | Core API Service | 8082 (внутренний) |
| `messenger_websocket` | WebSocket Server | 8092 (внутренний) |
| `messenger_gateway` | API Gateway | 8083 |
| `messenger_frontend` | React + Nginx | 80 |

## Деплой на VPS

### 1. Скопируйте проект на VPS

```bash
# Вариант A: через SCP (с Windows)
scp -r D:\androidxx\messanger_dip1 user@YOUR_VPS_IP:/home/user/messenger

# Вариант B: через git
ssh user@YOUR_VPS_IP
git clone https://github.com/your/repo.git messenger
cd messenger
```

### 2. Настройте `.env`

```bash
cd messenger
nano .env
```

Обязательно замените:
```env
POSTGRES_PASSWORD=сильный_пароль
JWT_SECRET=очень_длинная_случайная_строка_минимум_64_символа
TURN_SECRET=секрет_для_turn
```

### 3. Запустите деплой

```bash
chmod +x deploy.sh
bash deploy.sh
```

Скрипт автоматически:
- Установит Docker если нужно
- Определит публичный IP и пропишет в coturn
- Откроет нужные порты (80, 443, 3478, 49200-49300)
- Соберёт и запустит все контейнеры

### 4. Проверьте работу

```bash
docker compose ps          # статус контейнеров
docker compose logs -f     # логи всех сервисов
```

Приложение: `http://YOUR_VPS_IP`

---

## Настройка домена + HTTPS (опционально)

### Установите Nginx + Certbot на хосте

```bash
sudo apt install nginx certbot python3-certbot-nginx -y

# Получите SSL сертификат
sudo certbot --nginx -d your-domain.com

# Создайте конфиг Nginx
sudo nano /etc/nginx/sites-available/messenger
```

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:80;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    location /ws/ {
        proxy_pass http://localhost:8083/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;
    }
}

server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}
```

```bash
sudo ln -s /etc/nginx/sites-available/messenger /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

### Обновите `.env` для домена

```env
# В docker-compose.yml → frontend → args:
REACT_APP_GATEWAY_URL=https://your-domain.com
REACT_APP_WS_URL=wss://your-domain.com
```

И пересоберите фронтенд:
```bash
docker compose build frontend
docker compose up -d frontend
```

---

## Полезные команды

```bash
# Перезапустить конкретный сервис
docker compose restart auth-service

# Посмотреть логи одного сервиса
docker compose logs -f core-api-service

# Пересобрать один сервис (после изменения кода)
docker compose build gateway
docker compose up -d gateway

# Подключиться к PostgreSQL
docker compose exec postgres psql -U postgres -d messenger_db

# Обновить всё после изменений
docker compose build --no-cache
docker compose up -d
```

## Архитектура сети

```
Пользователь
    │
    ▼
[Nginx :80] ──────────────────── Frontend (React)
    │
    ├─ /auth/*  ──► [Gateway :8083] ──► [Auth Service :8081] ──► [PostgreSQL]
    ├─ /api/*   ──►       │          ──► [Core API :8082]    ──► [PostgreSQL]
    │                     │                                   ──► [Kafka]
    └─ /ws/*   ──►        │          ──► [WebSocket :8092]   ──► [Kafka]
                          │
                     [coturn :3478]  ← WebRTC TURN/STUN
```
