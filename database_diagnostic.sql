-- ===================================================
-- ДИАГНОСТИЧЕСКИЙ СКРИПТ ДЛЯ ПРОВЕРКИ БАЗЫ ДАННЫХ
-- ===================================================

-- Подключение к базе данных
\c messenger_db;

-- Проверка существующих таблиц
SELECT
    schemaname,
    tablename,
    tableowner,
    hasindexes,
    hasrules,
    hastriggers
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY tablename;

-- Проверка структуры таблицы users (если существует)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'users'
    ) THEN
        RAISE NOTICE 'Таблица users существует. Проверяем её структуру:';
    ELSE
        RAISE NOTICE 'Таблица users НЕ существует!';
    END IF;
END $$;

-- Показать столбцы таблицы users
SELECT
    column_name,
    data_type,
    is_nullable,
    column_default,
    character_maximum_length
FROM information_schema.columns
WHERE table_name = 'users' AND table_schema = 'public'
ORDER BY ordinal_position;

-- Проверка ограничений внешних ключей
SELECT
    tc.constraint_name,
    tc.table_name,
    kcu.column_name,
    ccu.table_name AS foreign_table_name,
    ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
    AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
    AND tc.table_schema = 'public';

-- Проверка первичных ключей
SELECT
    tc.table_name,
    kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
WHERE tc.constraint_type = 'PRIMARY KEY'
    AND tc.table_schema = 'public'
ORDER BY tc.table_name;

-- Проверка последовательностей (sequences)
SELECT
    schemaname,
    sequencename,
    start_value,
    min_value,
    max_value,
    increment_by
FROM pg_sequences
WHERE schemaname = 'public';

-- Проверка индексов
SELECT
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;

-- Проверка триггеров
SELECT
    trigger_name,
    event_manipulation,
    event_object_table,
    action_statement
FROM information_schema.triggers
WHERE trigger_schema = 'public'
ORDER BY event_object_table, trigger_name;

-- Проверка функций
SELECT
    routine_name,
    routine_type,
    data_type
FROM information_schema.routines
WHERE routine_schema = 'public'
ORDER BY routine_name;

-- Показать количество записей в таблицах (если они существуют)
DO $$
DECLARE
    rec RECORD;
    sql_text TEXT;
    count_result INTEGER;
BEGIN
    FOR rec IN
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
    LOOP
        sql_text := 'SELECT COUNT(*) FROM ' || rec.table_name;
        EXECUTE sql_text INTO count_result;
        RAISE NOTICE 'Таблица %: % записей', rec.table_name, count_result;
    END LOOP;
END $$;
