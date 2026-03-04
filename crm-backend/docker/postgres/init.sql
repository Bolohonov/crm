-- Инициализация БД при первом старте контейнера
-- Расширения PostgreSQL которые нужны приложению

-- gen_random_uuid() — генерация UUID v4 (используется в DEFAULT значениях)
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- pg_trgm — для fuzzy-поиска по тексту (поиск клиентов по имени)
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- unaccent — нормализация текста при поиске (ё -> е, etc.)
CREATE EXTENSION IF NOT EXISTS "unaccent";

COMMENT ON DATABASE crm_db IS 'CRM Cloud - основная база данных';
