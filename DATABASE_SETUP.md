# PostgreSQL Database Setup Commands для Messenger Project

## ⚠️ РЕШЕНИЕ ПРОБЛЕМ С СОЗДАНИЕМ БД

Если вы столкнулись с ошибками типа "столбец 'id' не существует" при создании внешних ключей, используйте один из следующих скриптов:

### Вариант 1: Основной скрипт (безопасный)
```bash
# Выполнение основного скрипта с проверками
psql -h localhost -p 5432 -U postgres -d messenger_db -f database_setup.sql
```

### Вариант 2: Полное пересоздание (если есть проблемы)
```bash
# Полная очистка и пересоздание базы данных
psql -h localhost -p 5432 -U postgres -d messenger_db -f database_reset.sql
```

### Вариант 3: Диагностика проблем
```bash
# Проверка текущего состояния базы данных
psql -h localhost -p 5432 -U postgres -d messenger_db -f database_diagnostic.sql
```

## 📁 Файлы в проекте

- **`database_setup.sql`** - основной скрипт с проверками существующих таблиц
- **`database_reset.sql`** - скрипт для полного пересоздания БД
- **`database_diagnostic.sql`** - диагностика состояния БД

## 1. Запуск PostgreSQL через Docker Compose

Убедитесь, что Docker установлен и запущен, затем выполните:

```bash
# Запуск контейнеров
docker-compose up -d postgres

# Проверка статуса
docker-compose ps
```

## 2. Подключение к базе данных

### Через Docker:
```bash
# Подключение к контейнeru PostgreSQL
docker exec -it messenger_postgres psql -U postgres -d messenger_db
```

### Через psql (если PostgreSQL установлен локально):
```bash
psql -h localhost -p 5432 -U postgres -d messenger_db
```

## 3. Выполнение SQL скрипта

### ✅ РЕКОМЕНДУЕМЫЙ способ - через Docker:
```bash
# Копирование файлов в контейнер
docker cp database_setup.sql messenger_postgres:/tmp/
docker cp database_reset.sql messenger_postgres:/tmp/
docker cp database_diagnostic.sql messenger_postgres:/tmp/

# Выполнение основного скрипта
docker exec -it messenger_postgres psql -U postgres -d messenger_db -f /tmp/database_setup.sql
```

### Локально:
```bash
psql -h localhost -p 5432 -U postgres -d messenger_db -f database_setup.sql
```

## 4. Быстрая установка (одна команда)

### Основная установка:
```bash
docker-compose up -d postgres && \
sleep 15 && \
docker cp database_setup.sql messenger_postgres:/tmp/ && \
docker exec -it messenger_postgres psql -U postgres -d messenger_db -f /tmp/database_setup.sql
```

### При проблемах - полное пересоздание:
```bash
docker-compose up -d postgres && \
sleep 15 && \
docker cp database_reset.sql messenger_postgres:/tmp/ && \
docker exec -it messenger_postgres psql -U postgres -d messenger_db -f /tmp/database_reset.sql
```

## 5. Проверка подключения из приложения

### Параметры подключения (из application.yml):
- **URL:** jdbc:postgresql://localhost:5432/messenger_db
- **Username:** postgres  
- **Password:** password

## 6. Диагностика и решение проблем

### 🔍 Проверка состояния БД:
```bash
docker exec -it messenger_postgres psql -U postgres -d messenger_db -f /tmp/database_diagnostic.sql
```

### 🚨 Если возникают ошибки внешних ключей:
1. **Причина**: Таблица `users` существует, но без правильной структуры
2. **Решение**: Используйте `database_reset.sql` для полного пересоздания

### 📝 Типичные ошибки и решения:

#### Ошибка: "столбец 'id' не существует"
```bash
# Выполните диагностику
docker exec -it messenger_postgres psql -U postgres -d messenger_db -f /tmp/database_diagnostic.sql

# Затем пересоздайте БД
docker exec -it messenger_postgres psql -U postgres -d messenger_db -f /tmp/database_reset.sql
```

#### Ошибка: "отношение 'users' уже существует"
```bash
# Используйте основной скрипт - он проверит и дополнит структуру
docker exec -it messenger_postgres psql -U postgres -d messenger_db -f /tmp/database_setup.sql
```

## 7. Полезные команды для отладки

### Проверка логов PostgreSQL:
```bash
docker logs messenger_postgres
```

### Проверка подключения:
```bash
docker exec -it messenger_postgres pg_isready -U postgres
```

### Резервное копирование:
```bash
docker exec -t messenger_postgres pg_dump -U postgres messenger_db > backup.sql
```

### Восстановление из резервной копии:
```bash
docker exec -i messenger_postgres psql -U postgres -d messenger_db < backup.sql
```

## 8. Решение проблем подключения

Если возникает ошибка "password authentication failed":

1. Убедитесь, что контейнер полностью запущен (подождите ~15 секунд)
2. Проверьте статус: `docker-compose ps`
3. Перезапустите контейнер: `docker-compose restart postgres`
4. Если проблема не решается: `docker-compose down && docker-compose up -d postgres`

## 9. Структура базы данных

После выполнения скрипта будут созданы:

### Таблицы:
- **users** - пользователи системы (основная таблица)
- **chats** - чаты (приватные и групповые, зависит от users)  
- **messages** - сообщения (зависит от users и chats)
- **chat_participants** - связь пользователей и чатов (зависит от users и chats)

### Индексы для оптимизации:
- По username, email пользователей
- По типу чатов и сообщений
- По времени отправки сообщений

### Триггеры:
- Автоматическое обновление `updated_at`
- Обновление `last_activity` в чатах

### Тестовые данные:
- 3 тестовых пользователя (`john_doe`, `jane_smith`, `admin`)
- 2 чата (групповой и приватный)
- 5 тестовых сообщений

## 10. Проверка успешной установки

После выполнения скрипта проверьте:

```sql
-- Подключитесь к БД
docker exec -it messenger_postgres psql -U postgres -d messenger_db

-- Проверьте таблицы
\dt

-- Проверьте данные
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM chats;
SELECT COUNT(*) FROM messages;
SELECT COUNT(*) FROM chat_participants;

-- Выход
\q
```

## 11. Устранение конфликтов

### Если Spring Boot не может подключиться:
1. Проверьте, что PostgreSQL запущен: `docker-compose ps`
2. Проверьте структуру БД: выполните `database_diagnostic.sql`
3. При необходимости пересоздайте БД: выполните `database_reset.sql`
4. Перезапустите Spring Boot приложение
