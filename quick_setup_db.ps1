# Быстрые команды PowerShell для создания БД в контейнере PostgreSQL
# Выполните эти команды по порядку в PowerShell

Write-Host "=== БЫСТРАЯ НАСТРОЙКА БАЗЫ ДАННЫХ ===" -ForegroundColor Green

# 1. ЗАПУСК КОНТЕЙНЕРА
Write-Host "1. Запуск PostgreSQL контейнера..." -ForegroundColor Yellow
docker-compose up -d postgres

# 2. ОЖИДАНИЕ ЗАПУСКА
Write-Host "2. Ожидание запуска (15 сек)..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# 3. СОЗДАНИЕ БАЗЫ ДАННЫХ
Write-Host "3. Создание базы данных messenger_db..." -ForegroundColor Yellow
docker exec messenger_postgres psql -U postgres -c "CREATE DATABASE messenger_db;"

# 4. КОПИРОВАНИЕ SQL ФАЙЛОВ (если они существуют)
Write-Host "4. Копирование SQL файлов..." -ForegroundColor Yellow
if (Test-Path "database_setup.sql") {
    docker cp database_setup.sql messenger_postgres:/tmp/
    Write-Host "✅ database_setup.sql скопирован" -ForegroundColor Green
} else {
    Write-Host "⚠️ database_setup.sql не найден - создам базовую структуру" -ForegroundColor Yellow
}

if (Test-Path "database_reset.sql") {
    docker cp database_reset.sql messenger_postgres:/tmp/
    Write-Host "✅ database_reset.sql скопирован" -ForegroundColor Green
}

if (Test-Path "database_diagnostic.sql") {
    docker cp database_diagnostic.sql messenger_postgres:/tmp/
    Write-Host "✅ database_diagnostic.sql скопирован" -ForegroundColor Green
}

# 5. СОЗДАНИЕ СТРУКТУРЫ БД
Write-Host "5. Создание структуры базы данных..." -ForegroundColor Yellow
if (Test-Path "database_setup.sql") {
    docker exec messenger_postgres psql -U postgres -d messenger_db -f /tmp/database_setup.sql
} else {
    # Создаем базовую структуру прямо через команды
    Write-Host "Создание базовых таблиц..." -ForegroundColor Yellow

    docker exec messenger_postgres psql -U postgres -d messenger_db -c "
    -- Создание таблицы пользователей
    CREATE TABLE IF NOT EXISTS users (
        id BIGSERIAL PRIMARY KEY,
        username VARCHAR(255) UNIQUE NOT NULL,
        password VARCHAR(255) NOT NULL,
        email VARCHAR(255) UNIQUE NOT NULL,
        display_name VARCHAR(255),
        avatar_url VARCHAR(500),
        bio TEXT,
        status VARCHAR(50) DEFAULT 'OFFLINE' CHECK (status IN ('ONLINE', 'OFFLINE', 'AWAY', 'BUSY')),
        last_seen TIMESTAMP,
        is_online BOOLEAN DEFAULT FALSE,
        last_seen_at TIMESTAMP,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

    -- Создание таблицы чатов
    CREATE TABLE IF NOT EXISTS chats (
        id BIGSERIAL PRIMARY KEY,
        name VARCHAR(255),
        type VARCHAR(50) DEFAULT 'PRIVATE' CHECK (type IN ('PRIVATE', 'GROUP')),
        description TEXT,
        avatar_url VARCHAR(500),
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        last_activity TIMESTAMP,
        created_by BIGINT,
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
    );

    -- Создание таблицы сообщений
    CREATE TABLE IF NOT EXISTS messages (
        id BIGSERIAL PRIMARY KEY,
        content TEXT NOT NULL,
        type VARCHAR(50) DEFAULT 'TEXT' CHECK (type IN ('TEXT', 'IMAGE', 'FILE', 'AUDIO', 'VIDEO')),
        sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        sender_id BIGINT NOT NULL,
        chat_id BIGINT NOT NULL,
        is_edited BOOLEAN DEFAULT FALSE,
        edited_at TIMESTAMP,
        status VARCHAR(50) DEFAULT 'SENT' CHECK (status IN ('SENT', 'DELIVERED', 'READ')),
        FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
    );

    -- Создание таблицы участников чатов
    CREATE TABLE IF NOT EXISTS chat_participants (
        chat_id BIGINT NOT NULL,
        user_id BIGINT NOT NULL,
        joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (chat_id, user_id),
        FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
    );

    -- Создание индексов
    CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
    CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
    CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id);
    CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);
    CREATE INDEX IF NOT EXISTS idx_messages_sent_at ON messages(sent_at);
    "
}

# 6. ПРОВЕРКА РЕЗУЛЬТАТА
Write-Host "6. Проверка созданных таблиц..." -ForegroundColor Yellow
docker exec messenger_postgres psql -U postgres -d messenger_db -c "\dt"

# 7. СОЗДАНИЕ ТЕСТОВЫХ ДАННЫХ
Write-Host "7. Создание тестовых данных..." -ForegroundColor Yellow
docker exec messenger_postgres psql -U postgres -d messenger_db -c "
INSERT INTO users (username, password, email, display_name) VALUES
('john_doe', 'password123', 'john@example.com', 'John Doe'),
('jane_smith', 'password123', 'jane@example.com', 'Jane Smith'),
('admin', 'admin123', 'admin@example.com', 'Administrator')
ON CONFLICT (username) DO NOTHING;
"

Write-Host "✅ НАСТРОЙКА ЗАВЕРШЕНА!" -ForegroundColor Green
Write-Host ""
Write-Host "📝 Параметры подключения:" -ForegroundColor Cyan
Write-Host "   URL: jdbc:postgresql://localhost:5432/messenger_db"
Write-Host "   Username: postgres"
Write-Host "   Password: password"
Write-Host ""
Write-Host "🔧 Команды для отладки:" -ForegroundColor Cyan
Write-Host "   docker logs messenger_postgres"
Write-Host "   docker exec -it messenger_postgres psql -U postgres -d messenger_db"
