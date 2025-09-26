# PowerShell скрипт для создания базы данных в контейнере PostgreSQL
# Messenger Project Database Setup

Write-Host "🚀 Настройка базы данных PostgreSQL для Messenger..." -ForegroundColor Green

# 1. Проверяем, запущен ли Docker
Write-Host "📋 Проверка Docker..." -ForegroundColor Yellow
try {
    docker --version
    Write-Host "✅ Docker найден" -ForegroundColor Green
} catch {
    Write-Host "❌ Docker не найден. Установите Docker Desktop" -ForegroundColor Red
    exit 1
}

# 2. Запускаем PostgreSQL контейнер
Write-Host "📦 Запуск PostgreSQL контейнера..." -ForegroundColor Yellow
docker-compose up -d postgres

# Ждем запуска контейнера
Write-Host "⏳ Ожидание запуска PostgreSQL (15 секунд)..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# 3. Проверяем статус контейнера
Write-Host "🔍 Проверка статуса контейнера..." -ForegroundColor Yellow
docker-compose ps

# 4. Создаем базу данных messenger_db (если не существует)
Write-Host "🗄️ Создание базы данных messenger_db..." -ForegroundColor Yellow
docker exec messenger_postgres psql -U postgres -c "CREATE DATABASE messenger_db;" 2>$null
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ База данных messenger_db создана" -ForegroundColor Green
} else {
    Write-Host "ℹ️ База данных messenger_db уже существует" -ForegroundColor Blue
}

# 5. Копируем SQL файлы в контейнер
Write-Host "📁 Копирование SQL файлов в контейнер..." -ForegroundColor Yellow

# Проверяем существование файлов
$sqlFiles = @("database_setup.sql", "database_reset.sql", "database_diagnostic.sql")
foreach ($file in $sqlFiles) {
    if (Test-Path $file) {
        docker cp $file messenger_postgres:/tmp/
        Write-Host "✅ Скопирован: $file" -ForegroundColor Green
    } else {
        Write-Host "⚠️ Файл не найден: $file" -ForegroundColor Yellow
    }
}

# 6. Выполняем диагностику
Write-Host "🔍 Диагностика состояния базы данных..." -ForegroundColor Yellow
if (Test-Path "database_diagnostic.sql") {
    docker exec messenger_postgres psql -U postgres -d messenger_db -f /tmp/database_diagnostic.sql
}

# 7. Создаем структуру базы данных
Write-Host "🏗️ Создание структуры базы данных..." -ForegroundColor Yellow
if (Test-Path "database_setup.sql") {
    docker exec messenger_postgres psql -U postgres -d messenger_db -f /tmp/database_setup.sql
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✅ Структура базы данных создана успешно" -ForegroundColor Green
    } else {
        Write-Host "❌ Ошибка при создании структуры. Попробуем полное пересоздание..." -ForegroundColor Red
        if (Test-Path "database_reset.sql") {
            docker exec messenger_postgres psql -U postgres -d messenger_db -f /tmp/database_reset.sql
        }
    }
} else {
    Write-Host "❌ Файл database_setup.sql не найден!" -ForegroundColor Red
    Write-Host "Создаем базовую структуру таблиц..." -ForegroundColor Yellow

    # Создаем базовую структуру прямо через команды
    $createTablesSQL = @"
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

CREATE TABLE IF NOT EXISTS chat_participants (
    chat_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (chat_id, user_id),
    FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_sent_at ON messages(sent_at);
"@

    # Сохраняем SQL во временный файл и выполняем
    $createTablesSQL | Out-File -FilePath "temp_create_tables.sql" -Encoding UTF8
    docker cp "temp_create_tables.sql" messenger_postgres:/tmp/
    docker exec messenger_postgres psql -U postgres -d messenger_db -f /tmp/temp_create_tables.sql
    Remove-Item "temp_create_tables.sql" -ErrorAction SilentlyContinue
}

# 8. Проверяем созданные таблицы
Write-Host "🔍 Проверка созданных таблиц..." -ForegroundColor Yellow
docker exec messenger_postgres psql -U postgres -d messenger_db -c "\dt"

# 9. Показываем количество записей в таблицах
Write-Host "📊 Проверка данных в таблицах..." -ForegroundColor Yellow
$countSQL = "SELECT 'users' as table_name, COUNT(*) as records FROM users UNION ALL SELECT 'chats' as table_name, COUNT(*) as records FROM chats UNION ALL SELECT 'messages' as table_name, COUNT(*) as records FROM messages UNION ALL SELECT 'chat_participants' as table_name, COUNT(*) as records FROM chat_participants;"

docker exec messenger_postgres psql -U postgres -d messenger_db -c $countSQL

# 10. Создаем тестовые данные
Write-Host "👤 Создание тестовых пользователей..." -ForegroundColor Yellow
$testDataSQL = @"
INSERT INTO users (username, password, email, display_name) VALUES
('john_doe', 'password123', 'john@example.com', 'John Doe'),
('jane_smith', 'password123', 'jane@example.com', 'Jane Smith'),
('admin', 'admin123', 'admin@example.com', 'Administrator')
ON CONFLICT (username) DO NOTHING;
"@

$testDataSQL | Out-File -FilePath "temp_test_data.sql" -Encoding UTF8
docker cp "temp_test_data.sql" messenger_postgres:/tmp/
docker exec messenger_postgres psql -U postgres -d messenger_db -f /tmp/temp_test_data.sql
Remove-Item "temp_test_data.sql" -ErrorAction SilentlyContinue

Write-Host "🎉 Настройка базы данных завершена!" -ForegroundColor Green
Write-Host ""
Write-Host "📝 Параметры подключения:" -ForegroundColor Cyan
Write-Host "   URL: jdbc:postgresql://localhost:5432/messenger_db" -ForegroundColor White
Write-Host "   Username: postgres" -ForegroundColor White
Write-Host "   Password: password" -ForegroundColor White
Write-Host ""
Write-Host "🔧 Для отладки используйте:" -ForegroundColor Cyan
Write-Host "   docker logs messenger_postgres" -ForegroundColor White
Write-Host "   docker exec -it messenger_postgres psql -U postgres -d messenger_db" -ForegroundColor White
