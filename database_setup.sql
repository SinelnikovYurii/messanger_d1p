-- ===================================================
-- PostgreSQL Database Setup для Messenger Application
-- ===================================================

-- 1. Создание базы данных (выполняется от суперпользователя)
-- CREATE DATABASE messenger_db;

-- 2. Создание пользователя и предоставление прав
-- CREATE USER messenger_user WITH PASSWORD 'password';
-- GRANT ALL PRIVILEGES ON DATABASE messenger_db TO messenger_user;

-- Подключение к базе данных messenger_db
\c messenger_db;

-- 3. Создание расширений
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ===================================================
-- УДАЛЕНИЕ СУЩЕСТВУЮЩИХ ТАБЛИЦ (ЕСЛИ НУЖНО ПЕРЕСОЗДАТЬ)
-- ===================================================
-- ВНИМАНИЕ: Раскомментируйте следующие строки только если нужно полностью пересоздать структуру

-- DROP TABLE IF EXISTS chat_participants CASCADE;
-- DROP TABLE IF EXISTS messages CASCADE;
-- DROP TABLE IF EXISTS chats CASCADE;
-- DROP TABLE IF EXISTS users CASCADE;

-- ===================================================
-- ПРОВЕРКА И СОЗДАНИЕ ТАБЛИЦ В ПРАВИЛЬНОМ ПОРЯДКЕ
-- ===================================================

-- Сначала проверяем существование таблицы users и её структуру
DO $$
BEGIN
    -- Проверяем существует ли таблица users с правильной структурой
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'users'
    ) THEN
        -- Если таблица не существует, создаём её
        CREATE TABLE users (
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
        RAISE NOTICE 'Таблица users создана';
    ELSE
        RAISE NOTICE 'Таблица users уже существует';

        -- Добавляем недостающие столбцы если они не существуют
        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'display_name'
        ) THEN
            ALTER TABLE users ADD COLUMN display_name VARCHAR(255);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'avatar_url'
        ) THEN
            ALTER TABLE users ADD COLUMN avatar_url VARCHAR(500);
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'bio'
        ) THEN
            ALTER TABLE users ADD COLUMN bio TEXT;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'status'
        ) THEN
            ALTER TABLE users ADD COLUMN status VARCHAR(50) DEFAULT 'OFFLINE' CHECK (status IN ('ONLINE', 'OFFLINE', 'AWAY', 'BUSY'));
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'last_seen'
        ) THEN
            ALTER TABLE users ADD COLUMN last_seen TIMESTAMP;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'is_online'
        ) THEN
            ALTER TABLE users ADD COLUMN is_online BOOLEAN DEFAULT FALSE;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'last_seen_at'
        ) THEN
            ALTER TABLE users ADD COLUMN last_seen_at TIMESTAMP;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'created_at'
        ) THEN
            ALTER TABLE users ADD COLUMN created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM information_schema.columns
            WHERE table_name = 'users' AND column_name = 'updated_at'
        ) THEN
            ALTER TABLE users ADD COLUMN updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
        END IF;
    END IF;
END $$;

-- Таблица чатов (создается после users)
CREATE TABLE IF NOT EXISTS chats (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    type VARCHAR(50) DEFAULT 'PRIVATE' CHECK (type IN ('PRIVATE', 'GROUP')),
    description TEXT,
    avatar_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL
);

-- Таблица сообщений (создается после users и chats)
CREATE TABLE IF NOT EXISTS messages (
    id BIGSERIAL PRIMARY KEY,
    content TEXT NOT NULL,
    type VARCHAR(50) DEFAULT 'TEXT' CHECK (type IN ('TEXT', 'IMAGE', 'FILE', 'AUDIO', 'VIDEO')),
    sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    sender_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chat_id BIGINT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    is_edited BOOLEAN DEFAULT FALSE,
    edited_at TIMESTAMP,
    status VARCHAR(50) DEFAULT 'SENT' CHECK (status IN ('SENT', 'DELIVERED', 'READ'))
);

-- Таблица участников чатов (создается после users и chats)
CREATE TABLE IF NOT EXISTS chat_participants (
    chat_id BIGINT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (chat_id, user_id)
);

-- ===================================================
-- СОЗДАНИЕ ИНДЕКСОВ ДЛЯ ОПТИМИЗАЦИИ
-- ===================================================

-- Индексы для таблицы users
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_is_online ON users(is_online);

-- Индексы для таблицы chats
CREATE INDEX IF NOT EXISTS idx_chats_type ON chats(type);
CREATE INDEX IF NOT EXISTS idx_chats_created_by ON chats(created_by);
CREATE INDEX IF NOT EXISTS idx_chats_last_activity ON chats(last_activity);

-- Индексы для таблицы messages
CREATE INDEX IF NOT EXISTS idx_messages_sender_id ON messages(sender_id);
CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_messages_sent_at ON messages(sent_at);
CREATE INDEX IF NOT EXISTS idx_messages_type ON messages(type);
CREATE INDEX IF NOT EXISTS idx_messages_status ON messages(status);
CREATE INDEX IF NOT EXISTS idx_messages_chat_sent_at ON messages(chat_id, sent_at);

-- Индексы для таблицы chat_participants
CREATE INDEX IF NOT EXISTS idx_chat_participants_user_id ON chat_participants(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_participants_joined_at ON chat_participants(joined_at);

-- ===================================================
-- СОЗДАНИЕ ФУНКЦИЙ И ТРИГГЕРОВ
-- ===================================================

-- Функция для автоматического обновления updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггеры для автоматического обновления updated_at
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_chats_updated_at ON chats;
CREATE TRIGGER update_chats_updated_at
    BEFORE UPDATE ON chats
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Функция для обновления last_activity в чате при добавлении сообщения
CREATE OR REPLACE FUNCTION update_chat_last_activity()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE chats
    SET last_activity = NEW.sent_at
    WHERE id = NEW.chat_id;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Триггер для обновления last_activity
DROP TRIGGER IF EXISTS update_chat_last_activity_trigger ON messages;
CREATE TRIGGER update_chat_last_activity_trigger
    AFTER INSERT ON messages
    FOR EACH ROW EXECUTE FUNCTION update_chat_last_activity();

-- ===================================================
-- ВСТАВКА ТЕСТОВЫХ ДАННЫХ
-- ===================================================

-- Проверяем, есть ли уже данные, и вставляем только если их нет
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'john_doe') THEN
        INSERT INTO users (username, password, email, display_name, bio, status, is_online) VALUES
        ('john_doe', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'john@example.com', 'John Doe', 'Software Developer', 'ONLINE', true);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'jane_smith') THEN
        INSERT INTO users (username, password, email, display_name, bio, status, is_online) VALUES
        ('jane_smith', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'jane@example.com', 'Jane Smith', 'UI/UX Designer', 'OFFLINE', false);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin') THEN
        INSERT INTO users (username, password, email, display_name, bio, status, is_online) VALUES
        ('admin', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'admin@example.com', 'Administrator', 'System Administrator', 'ONLINE', true);
    END IF;
END $$;

-- Тестовые чаты (только если их еще нет)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM chats WHERE name = 'General Chat') THEN
        INSERT INTO chats (name, type, description, created_by) VALUES
        ('General Chat', 'GROUP', 'Main discussion group', (SELECT id FROM users WHERE username = 'john_doe'));
    END IF;

    IF NOT EXISTS (SELECT 1 FROM chats WHERE name = 'John & Jane') THEN
        INSERT INTO chats (name, type, description, created_by) VALUES
        ('John & Jane', 'PRIVATE', 'Private conversation', (SELECT id FROM users WHERE username = 'john_doe'));
    END IF;
END $$;

-- Участники чатов (только если их еще нет)
DO $$
DECLARE
    general_chat_id BIGINT;
    private_chat_id BIGINT;
    john_id BIGINT;
    jane_id BIGINT;
    admin_id BIGINT;
BEGIN
    SELECT id INTO general_chat_id FROM chats WHERE name = 'General Chat';
    SELECT id INTO private_chat_id FROM chats WHERE name = 'John & Jane';
    SELECT id INTO john_id FROM users WHERE username = 'john_doe';
    SELECT id INTO jane_id FROM users WHERE username = 'jane_smith';
    SELECT id INTO admin_id FROM users WHERE username = 'admin';

    -- General Chat participants
    INSERT INTO chat_participants (chat_id, user_id) VALUES (general_chat_id, john_id)
    ON CONFLICT (chat_id, user_id) DO NOTHING;

    INSERT INTO chat_participants (chat_id, user_id) VALUES (general_chat_id, jane_id)
    ON CONFLICT (chat_id, user_id) DO NOTHING;

    INSERT INTO chat_participants (chat_id, user_id) VALUES (general_chat_id, admin_id)
    ON CONFLICT (chat_id, user_id) DO NOTHING;

    -- Private Chat participants
    INSERT INTO chat_participants (chat_id, user_id) VALUES (private_chat_id, john_id)
    ON CONFLICT (chat_id, user_id) DO NOTHING;

    INSERT INTO chat_participants (chat_id, user_id) VALUES (private_chat_id, jane_id)
    ON CONFLICT (chat_id, user_id) DO NOTHING;
END $$;

-- Тестовые сообщения (только если их еще нет)
DO $$
DECLARE
    general_chat_id BIGINT;
    private_chat_id BIGINT;
    john_id BIGINT;
    jane_id BIGINT;
BEGIN
    SELECT id INTO general_chat_id FROM chats WHERE name = 'General Chat';
    SELECT id INTO private_chat_id FROM chats WHERE name = 'John & Jane';
    SELECT id INTO john_id FROM users WHERE username = 'john_doe';
    SELECT id INTO jane_id FROM users WHERE username = 'jane_smith';

    IF NOT EXISTS (SELECT 1 FROM messages WHERE content = 'Hello everyone!' AND sender_id = john_id) THEN
        INSERT INTO messages (content, sender_id, chat_id) VALUES
        ('Hello everyone!', john_id, general_chat_id),
        ('Hi John! How are you?', jane_id, general_chat_id),
        ('I am doing great, thanks!', john_id, general_chat_id),
        ('Hey Jane, want to discuss the project?', john_id, private_chat_id),
        ('Sure! Let me know when you are free.', jane_id, private_chat_id);
    END IF;
END $$;

-- ===================================================
-- ПРЕДОСТАВЛЕНИЕ ПРАВ ПОЛЬЗОВАТЕЛЮ
-- ===================================================

-- Предоставление прав на все таблицы
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;

-- ===================================================
-- ПРОВЕРКА СОЗДАННОЙ СТРУКТУРЫ
-- ===================================================

-- Показать созданные таблицы
SELECT
    table_name,
    (SELECT COUNT(*) FROM information_schema.columns WHERE table_name = t.table_name) as column_count
FROM information_schema.tables t
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- Показать количество записей в каждой таблице
SELECT
    'users' as table_name,
    COUNT(*) as record_count
FROM users
UNION ALL
SELECT
    'chats' as table_name,
    COUNT(*) as record_count
FROM chats
UNION ALL
SELECT
    'messages' as table_name,
    COUNT(*) as record_count
FROM messages
UNION ALL
SELECT
    'chat_participants' as table_name,
    COUNT(*) as record_count
FROM chat_participants;

ALTER TABLE chats ADD COLUMN chat_type varchar(255);
UPDATE chats SET chat_type='PRIVATE' WHERE chat_type IS NULL;
ALTER TABLE chats ALTER COLUMN chat_type SET NOT NULL;
ALTER TABLE chats ADD CONSTRAINT chat_type_chk CHECK (chat_type IN ('PRIVATE','GROUP'));

ALTER TABLE messages ADD COLUMN message_type varchar(255);
UPDATE messages SET message_type='TEXT' WHERE message_type IS NULL;
ALTER TABLE messages ALTER COLUMN message_type SET NOT NULL;
ALTER TABLE messages ADD CONSTRAINT message_type_chk CHECK (message_type IN ('TEXT','IMAGE','FILE','VOICE','SYSTEM'));


-- ===================================================
-- ПОЛЕЗНЫЕ ЗАПРОСЫ ДЛЯ ПРОВЕРКИ
-- ===================================================

-- Проверка пользователей
-- SELECT id, username, email, display_name, status, is_online FROM users;

-- Проверка чатов с участниками
-- SELECT c.id, c.name, c.type, COUNT(cp.user_id) as participant_count
-- FROM chats c
-- LEFT JOIN chat_participants cp ON c.id = cp.chat_id
-- GROUP BY c.id, c.name, c.type;

-- Проверка сообщений
-- SELECT m.id, u.username as sender, c.name as chat, m.content, m.sent_at
-- FROM messages m
-- JOIN users u ON m.sender_id = u.id
-- JOIN chats c ON m.chat_id = c.id
-- ORDER BY m.sent_at DESC;
