-- Добавляем поля для хранения метаданных файлов в таблицу messages

ALTER TABLE messages
ADD COLUMN file_url VARCHAR(500),
ADD COLUMN file_name VARCHAR(255),
ADD COLUMN file_size BIGINT,
ADD COLUMN mime_type VARCHAR(100),
ADD COLUMN thumbnail_url VARCHAR(500);

-- Создаем индексы для оптимизации поиска
CREATE INDEX idx_messages_file_url ON messages(file_url);
CREATE INDEX idx_messages_message_type ON messages(message_type);

-- Комментарии к полям
COMMENT ON COLUMN messages.file_url IS 'URL для доступа к файлу';
COMMENT ON COLUMN messages.file_name IS 'Оригинальное имя файла';
COMMENT ON COLUMN messages.file_size IS 'Размер файла в байтах';
COMMENT ON COLUMN messages.mime_type IS 'MIME-тип файла';
COMMENT ON COLUMN messages.thumbnail_url IS 'URL превью изображения';

