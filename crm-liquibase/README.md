# CRM — Liquibase Migrations

Полная схема базы данных: public (системные таблицы) + tenant (бизнес-данные, schema-per-tenant).

## Структура

```
db/
├── changelog-master.xml               ← корневой changelog
└── migration/
    ├── public/                        ← системные таблицы (один раз)
    │   ├── V001__extensions.xml       — uuid-ossp, pg_trgm, unaccent, set_updated_at()
    │   ├── V002__tenants.xml          — реестр тенантов
    │   ├── V003__users_global.xml     — глобальные пользователи (auth)
    │   ├── V004__oauth_accounts.xml   — OAuth (Google, Yandex)
    │   ├── V005__refresh_tokens.xml   — JWT refresh tokens + cleanup function
    │   └── V006__tenant_modules.xml   — включённые модули по тенанту
    └── tenant/                        ← бизнес-данные в каждом tenant_* schema
        ├── V101__roles_permissions.xml — permissions + roles + role_permissions M2M
        ├── V102__users_tenant.xml      — профили пользователей + user_roles M2M
        ├── V103__statuses.xml          — справочник статусов заказов и задач
        ├── V104__customers.xml         — клиенты (юр. и физ. лица) с FTS
        ├── V105__tasks.xml             — задачи + комментарии
        ├── V106__products.xml          — каталог товаров и услуг с FTS
        ├── V107__orders.xml            — заказы + позиции (order_items)
        ├── V108__seed_roles_permissions.xml — системные роли (ADMIN, MANAGER, SALES, OBSERVER)
        ├── V109__seed_statuses.xml     — системные статусы заказов и задач
        ├── V110__demo_seed.xml         — демо-данные [context=demo]
        └── tenant-changelog.xml        ← changelog для программного создания тенантов
```

---

## Запуск

### Первичная инициализация (production)

```bash
# Только структура, без демо-данных
liquibase \
  --url=jdbc:postgresql://localhost:5432/crm \
  --username=crm_user \
  --password=secret \
  --changelog-file=db/changelog-master.xml \
  update
```

### Demo-стенд (с тестовыми данными)

```bash
liquibase \
  --url=jdbc:postgresql://localhost:5432/crm_demo \
  --username=crm_user \
  --password=secret \
  --changelog-file=db/changelog-master.xml \
  update --contexts=demo
```

Демо-данные (V110, `context=demo`):
- **25 товаров** — ИТ-услуги, оборудование, лицензии (цены 7 200–890 000 ₽)
- **60 клиентов** — 38 юр. лиц + 22 физ. лица, Москва/СПб/регионы
- **80 заказов** — воронка: NEW=18, IN_PROGRESS=22, WAITING=12, DONE=20, CANCELLED=8
- **50 задач** — TODO=12, IN_PROGRESS=14, DONE=14, просрочено=10 (дедлайны в прошлом)

### Spring Boot (автоматически при старте)

```yaml
# application.yml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog-master.xml
    default-schema: public
    contexts: ${LIQUIBASE_CONTEXTS:}   # передать "demo" для демо-стенда
```

---

## Создание нового тенанта

При регистрации нового тенанта вызывается `TenantMigrationService`:

```java
// Обычный тенант
tenantMigrationService.createTenantSchema("tenant_a3f7b2c9", false);

// Demo-тенант (с тестовыми данными)
tenantMigrationService.createTenantSchema("tenant_demo_01", true);
```

Сервис:
1. Создаёт PostgreSQL схему `tenant_{slug}`
2. Устанавливает `search_path` на неё
3. Применяет V101–V109 (структура + системные данные)
4. При `applyDemoData=true` — применяет V110 с `context=demo`

---

## Rollback

Все changeSet-ы имеют `<rollback>` блок:

```bash
# Откатить последние N changeSet-ов
liquibase rollbackCount 3

# Откатить до конкретного тега
liquibase tag v1.0.0
liquibase rollback v1.0.0
```

---

## Полезные команды

```bash
# Статус — какие changeSet-ы не применены
liquibase status --verbose

# Validate — проверить changelog без применения
liquibase validate

# Diff — сравнить схему с changelog
liquibase diff

# Отчёт о применённых изменениях
liquibase history

# Генерация SQL без выполнения
liquibase updateSQL > migration.sql
```

---

## Таблицы по схемам

### public (глобальные)

| Таблица | Описание |
|---|---|
| `tenants` | Реестр организаций-тенантов |
| `users_global` | Auth-идентификаторы пользователей |
| `oauth_accounts` | Linked OAuth аккаунты (Google, Yandex) |
| `refresh_tokens` | JWT refresh tokens с ротацией |
| `tenant_modules` | Включённые модули CRM по тенанту |

### tenant_* (per-tenant)

| Таблица | Описание |
|---|---|
| `permissions` | Атомарные права (20 шт: CUSTOMER_VIEW, ORDER_CREATE...) |
| `roles` | Роли с цветом (ADMIN, MANAGER, SALES, OBSERVER + кастомные) |
| `role_permissions` | M2M: роль ↔ права |
| `users` | Профили пользователей (ФИО, должность, аватар) |
| `user_roles` | M2M: пользователь ↔ роли |
| `order_statuses` | Справочник статусов заказов (воронка) |
| `task_statuses` | Справочник статусов задач |
| `customers` | Клиенты (юр./физ. лица), FTS по имени/ИНН/телефону |
| `tasks` | Задачи с дедлайнами, исполнителями, приоритетом |
| `task_comments` | Комментарии к задачам |
| `products` | Каталог товаров и услуг, FTS |
| `orders` | Заказы с денормализованным total_amount |
| `order_items` | Позиции заказов (денормализованное название, цена) |
