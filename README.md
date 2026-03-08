# Cloud CRM

Многотенантная CRM-система для управления клиентами, заказами и задачами. Разработана как pet-проект / портфолио.

**Live demo:** [bolohonovma.online/crm](https://bolohonovma.online/crm)

---

## Стек технологий

### Backend
- **Java 21** + **Spring Boot 3.3**
- **Spring Data JDBC** — без JPA/Hibernate, прямое управление SQL
- **Spring Security** + **JWT** (access + refresh токены)
- **PostgreSQL 16** — multi-tenant с изоляцией по схемам (`tenant_<uuid>`)
- **Liquibase** — отдельные changelog'и для `public` и tenant-схем
- **Apache Kafka** — интеграция с интернет-магазином (Transactional Outbox pattern)
- **Redis** — кэш, blacklist токенов
- **SSE (Server-Sent Events)** — real-time уведомления об изменениях заказов
- **Apache POI** — экспорт данных в Excel / CSV
- **Thymeleaf** — HTML-шаблоны email-уведомлений
- **SpringDoc OpenAPI** — автогенерация Swagger UI (`/api/v1/swagger-ui.html`)
- **MapStruct** — маппинг DTO ↔ entity

### Frontend
- **Vue 3** + **TypeScript**
- **PrimeVue 4** — UI-компоненты
- **Pinia** — state management
- **Vue Router 4**
- **Axios** — HTTP-клиент с interceptor для автообновления токенов
- **SortableJS** — drag-and-drop канбан-доска статусов
- **Vuelidate** — валидация форм
- **Day.js** — работа с датами

### Инфраструктура
- **k3s** (Kubernetes) — деплой на домашнем сервере
- **Helm** — пакетная сборка и деплой
- **Traefik** — Ingress-контроллер с `stripPrefix` middleware
- **WireGuard** — туннель VPS ↔ локальный сервер
- **Nginx** (на VPS) — проксирование трафика через туннель
- **Docker** — общие контейнеры Kafka и PostgreSQL вне k3s
- **GitHub Actions** — CI/CD (build → push в локальный registry → деплой через SSH)

---

## Архитектура

### Multi-tenancy
Каждый тенант получает отдельную PostgreSQL-схему (`tenant_<uuid>`). При регистрации admin-пользователя Liquibase автоматически провизионирует полную схему тенанта: таблицы, индексы, seed-данные для ролей/прав/статусов.

Переключение схемы происходит динамически на уровне HTTP-запроса через `TenantContext` + `search_path`.

```
public schema          tenant_abc123 schema       tenant_def456 schema
─────────────────      ──────────────────────      ──────────────────────
users_global           users, roles, permissions   users, roles, permissions
tenants                customers, orders, tasks     customers, orders, tasks
refresh_tokens         products, audit_log          products, audit_log
email_verifications    order_statuses, ...          order_statuses, ...
kafka_outbox           module_settings              module_settings
```

### RBAC
Роли и права хранятся в tenant-схеме. Системные роли (`ADMIN`, `MANAGER`, `SALES`, `OBSERVER`) создаются при провизионировании. Администратор может создавать кастомные роли и назначать любой набор прав из 20 предопределённых.

### Kafka-интеграция с интернет-магазином
Используется **Transactional Outbox Pattern**: события сначала записываются в таблицу `kafka_outbox` в одной транзакции с бизнес-данными, затем scheduler публикует их в Kafka.

```
Shop → [shop.orders.created] → CRM (создаёт заказ в tenant-схеме)
CRM  → [crm.orders.status_changed] → Shop (обновляет статус заказа)
CRM  → [crm.tenant.created] → Shop (регистрирует привязку тенанта)
```

Dead Letter Queue: `shop.orders.created.dlq` — для сообщений, которые не удалось обработать после 3 попыток.

---

## Структура проекта

```
crm_final/
├── crm-backend/          # Spring Boot приложение
│   ├── src/main/java/com/crm/
│   │   ├── auth/         # Регистрация, логин, JWT, email-верификация, OAuth2
│   │   ├── customer/     # Клиенты (физ. и юр. лица), поиск по FTS
│   │   ├── order/        # Заказы, позиции заказа
│   │   ├── task/         # Задачи, комментарии, типы задач
│   │   ├── product/      # Каталог товаров и услуг
│   │   ├── user/         # Управление пользователями тенанта
│   │   ├── rbac/         # Роли и права доступа
│   │   ├── status/       # Управление статусами заказов и задач
│   │   ├── dashboard/    # Статистика и аналитика
│   │   ├── audit/        # Лог изменений сущностей
│   │   ├── export/       # Экспорт в Excel / CSV
│   │   ├── sse/          # Server-Sent Events уведомления
│   │   ├── kafka/        # Producer, Consumer, Outbox, конфиги
│   │   ├── tenant/       # Провизионирование tenant-схем
│   │   └── common/       # Security config, исключения, утилиты
│   └── docker/postgres/  # init.sql с extensions
│
├── crm-frontend/         # Vue 3 + TypeScript SPA
│   └── src/
│       ├── views/        # Страницы: dashboard, customers, orders, tasks, admin
│       ├── components/   # Переиспользуемые компоненты
│       ├── stores/       # Pinia stores
│       ├── api/          # Axios-модули для каждого домена
│       ├── composables/  # Vue composables
│       └── types/        # TypeScript типы
│
├── crm-liquibase/        # Отдельный модуль миграций
│   └── src/main/resources/db/migration/
│       ├── public/       # Схема public: V001–V010 + kafka_infrastructure
│       └── tenant/       # Схема тенанта: единый V101__init.xml
│
└── crm-helm/             # Helm chart для деплоя в k3s
    └── templates/
        ├── backend.yaml
        ├── frontend.yaml
        ├── ingress.yaml
        └── secrets.yaml
```

---

## Функциональность

### Модули системы
- **Клиенты** — физ. и юр. лица, поиск по полнотекстовому индексу (PostgreSQL FTS), статусы, внешний ID для интеграции с магазином
- **Заказы** — жизненный цикл заказа (NEW → PICKING → SHIPPED → DELIVERED → ARCHIVED), позиции, интеграция с Kafka
- **Задачи** — типы задач, приоритеты, дедлайны, назначение исполнителя, drag-and-drop канбан
- **Товары** — каталог, артикулы, ценообразование
- **Пользователи** — приглашения, управление ролями, активность
- **Аналитика** — дашборд с метриками по заказам, клиентам, задачам
- **Аудит** — полный лог изменений каждой сущности с diff по полям (JSONB)
- **Экспорт** — выгрузка данных в `.xlsx` и `.csv` через Apache POI

### Аутентификация
- Email + пароль с подтверждением email
- OAuth2 (Google, Yandex)
- JWT access token (15 мин) + refresh token (30 дней)
- Автоматическое обновление токена через Axios interceptor

---

## Локальный запуск

### Требования
- Java 21+
- Node.js 20+
- Docker (PostgreSQL, Redis, Kafka)
- Maven 3.9+

### Backend

```bash
# 1. Запустить зависимости
docker run -d --name postgres -e POSTGRES_PASSWORD=pass -p 5432:5432 postgres:16-alpine
docker run -d --name redis -p 6379:6379 redis:7-alpine

# 2. Создать БД
docker exec postgres psql -U postgres -c "CREATE DATABASE crm_db; CREATE USER crm_user WITH PASSWORD 'crm_pass'; GRANT ALL ON DATABASE crm_db TO crm_user;"

# 3. Собрать и запустить
cd crm-backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

Swagger UI доступен по адресу: `http://localhost:8080/api/v1/swagger-ui.html`

### Frontend

```bash
cd crm-frontend
npm install
npm run dev
```

### Переменные окружения (backend)

| Переменная | Описание | По умолчанию |
|---|---|---|
| `DB_HOST` | PostgreSQL хост | `localhost` |
| `DB_PORT` | PostgreSQL порт | `5432` |
| `DB_NAME` | Имя БД | `crm_db` |
| `DB_USERNAME` | Пользователь БД | `crm_user` |
| `DB_PASSWORD` | Пароль БД | `crm_pass` |
| `REDIS_HOST` | Redis хост | `localhost` |
| `REDIS_PORT` | Redis порт | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap | `localhost:9092` |
| `JWT_SECRET` | Секрет для JWT | — |
| `MAIL_HOST` | SMTP сервер | `smtp.yandex.ru` |
| `MAIL_USERNAME` | SMTP логин | — |
| `MAIL_PASSWORD` | SMTP пароль | — |
| `GOOGLE_CLIENT_ID` | OAuth2 Google | — |
| `YANDEX_CLIENT_ID` | OAuth2 Yandex | — |

---

## Деплой

Деплой осуществляется через Helm в k3s-кластер на локальном сервере. Трафик проходит через VPS (Nginx) → WireGuard-туннель → Traefik → поды.

```bash
# Сборка образов и деплой
cd crm-helm
helm upgrade --install crm-app . -f values.yaml --namespace crm
```

Приложение доступно по адресу: `https://bolohonovma.online/crm`

---

## Схема базы данных

### Public-схема (общая)
- `tenants` — реестр тенантов
- `users_global` — глобальные учётные записи (auth-данные)
- `refresh_tokens` — refresh токены
- `email_verifications` — токены подтверждения email
- `oauth_accounts` — привязки OAuth2-аккаунтов
- `tenant_modules` — активные модули тенанта
- `kafka_outbox` — Transactional Outbox для Kafka-событий
- `kafka_idempotency_log` — дедупликация входящих Kafka-сообщений

### Tenant-схема (на каждого тенанта)
- `users` + `roles` + `permissions` + `user_roles` + `role_permissions`
- `customers` + `customer_personal_data` + `customer_org_data`
- `orders` + `order_items` + `order_statuses`
- `tasks` + `task_comments` + `task_types` + `task_statuses`
- `products`
- `audit_log`
- `module_settings`

---

## Автор

**Михаил Болохонов** — Java Backend Developer

- Telegram: [@Bolohonov](https://t.me/Bolohonov)
- Portfolio: [bolohonovma.online](https://bolohonovma.online)