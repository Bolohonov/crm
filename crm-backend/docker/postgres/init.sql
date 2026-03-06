-- Актуальный init.sql (опционально)
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "unaccent";
CREATE EXTENSION IF NOT EXISTS "btree_gin";

COMMENT ON DATABASE crm_db IS 'CRM Cloud - основная база данных';