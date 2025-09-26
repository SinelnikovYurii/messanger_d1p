-- ===================================================
-- СКРИПТ ДЛЯ ОЧИСТКИ И ПЕРЕСОЗДАНИЯ БАЗЫ ДАННЫХ
-- ===================================================

-- Подключение к базе данных messenger_db
\c messenger_db;

-- Удаление всех таблиц в правильном порядке (от зависимых к независимым)
DROP TABLE IF EXISTS chat_participants CASCADE;
DROP TABLE IF EXISTS messages CASCADE;
DROP TABLE IF EXISTS chats CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- Удаление функций
DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;
DROP FUNCTION IF EXISTS update_chat_last_activity() CASCADE;

-- Создание расширений
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ===================================================
-- СОЗДАНИЕ ТАБЛИЦ В ПРАВИЛЬНОМ ПОРЯДКЕ
-- ===================================================

-- Таблица пользователей (первая, независимая)
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

-- Таблица чатов (зависит от users)
CREATE TABLE chats (
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

-- Таблица сообщений (зависит от users и chats)
CREATE TABLE messages (
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

-- Таблица участников чатов (зависит от users и chats)
CREATE TABLE chat_participants (
    chat_id BIGINT NOT NULL REFERENCES chats(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (chat_id, user_id)
);

-- ===================================================
-- СОЗДАНИЕ ИНДЕКСОВ
-- ===================================================

-- Индексы для таблицы users
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_is_online ON users(is_online);

-- Индексы для таблицы chats
CREATE INDEX idx_chats_type ON chats(type);
CREATE INDEX idx_chats_created_by ON chats(created_by);
CREATE INDEX idx_chats_last_activity ON chats(last_activity);

-- Индексы для таблицы messages
CREATE INDEX idx_messages_sender_id ON messages(sender_id);
CREATE INDEX idx_messages_chat_id ON messages(chat_id);
CREATE INDEX idx_messages_sent_at ON messages(sent_at);
CREATE INDEX idx_messages_type ON messages(type);
CREATE INDEX idx_messages_status ON messages(status);
CREATE INDEX idx_messages_chat_sent_at ON messages(chat_id, sent_at);

-- Индексы для таблицы chat_participants
CREATE INDEX idx_chat_participants_user_id ON chat_participants(user_id);
CREATE INDEX idx_chat_participants_joined_at ON chat_participants(joined_at);

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
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

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
CREATE TRIGGER update_chat_last_activity_trigger
    AFTER INSERT ON messages
    FOR EACH ROW EXECUTE FUNCTION update_chat_last_activity();

-- ===================================================
-- ВСТАВКА ТЕСТОВЫХ ДАННЫХ
-- ===================================================

-- Тестовые пользователи
INSERT INTO users (username, password, email, display_name, bio, status, is_online) VALUES
('john_doe', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'john@example.com', 'John Doe', 'Software Developer', 'ONLINE', true),
('jane_smith', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'jane@example.com', 'Jane Smith', 'UI/UX Designer', 'OFFLINE', false),
('admin', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 'admin@example.com', 'Administrator', 'System Administrator', 'ONLINE', true);

-- Тестовые чаты
INSERT INTO chats (name, type, description, created_by) VALUES
('General Chat', 'GROUP', 'Main discussion group', 1),
('John & Jane', 'PRIVATE', 'Private conversation', 1);

-- Участники чатов
INSERT INTO chat_participants (chat_id, user_id) VALUES
(1, 1), (1, 2), (1, 3),  -- General Chat
(2, 1), (2, 2);          -- John & Jane

-- Тестовые сообщения
INSERT INTO messages (content, sender_id, chat_id) VALUES
('Hello everyone!', 1, 1),
('Hi John! How are you?', 2, 1),
('I am doing great, thanks!', 1, 1),
('Hey Jane, want to discuss the project?', 1, 2),
('Sure! Let me know when you are free.', 2, 2);

-- ===================================================
-- ПРЕДОСТАВЛЕНИЕ ПРАВ
-- ===================================================

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO postgres;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;

-- ===================================================
-- ПРОВЕРКА РЕЗУЛЬТАТА
-- ===================================================

-- Показать созданные таблицы и количество записей
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

RAISE NOTICE 'База данных успешно пересоздана!';
