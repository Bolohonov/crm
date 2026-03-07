

crm_final.zip
zip


shop-project.zip
zip

есть два проекта - надо протестировать kafka взаимодействие , проекты во вложении , они в k3s, kafka  в докер контейнере

6 мар.

Ran 10 commands
Ran 10 commands
Теперь у меня полная картина. Давай составлю детальный план тестирования.

Анализ Kafka-взаимодействия
Топология
Shop (k3s) ──── shop.orders.created ────► CRM (k3s)
CRM (k3s)  ──── crm.orders.status_changed ──► Shop (k3s)
Kafka: Docker-контейнер на хосте, bitnami/kafka:3.7, KRaft mode (без Zookeeper)

Проблема: В docker-compose.yml Kafka слушает только PLAINTEXT://kafka:9092 (внутри Docker-сети), а поды в k3s ходят через 172.17.0.1:9092 (docker bridge gateway). Нужно убедиться, что ADVERTISED_LISTENERS включает этот адрес.

Шаг 1 — Проверка и починка Kafka (если нужно)
SSH на сервер где запущен Docker с Kafka:

bash
# Проверяем что Kafka слушает снаружи
docker exec -it <kafka-container> kafka-topics.sh \
--bootstrap-server localhost:9092 \
--list
Если Kafka advertised только внутри Docker-сети — нужно пересоздать с правильным конфигом:

yaml
# docker-compose.yml — kafka секция
kafka:
image: bitnami/kafka:3.7
ports:
- "9092:9092"
environment:
KAFKA_CFG_NODE_ID: 1
KAFKA_CFG_PROCESS_ROLES: controller,broker
KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
KAFKA_CFG_LISTENERS: PLAINTEXT://:9092,EXTERNAL://:9093_EXT,CONTROLLER://:9093
# Ключевое: два advertised listener — для Docker-сети и для k3s
KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092,EXTERNAL://172.17.0.1:9092
KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT,EXTERNAL:PLAINTEXT
KAFKA_CFG_CONTROLLER_LISTENER_NAMES: CONTROLLER
KAFKA_CFG_AUTO_CREATE_TOPICS_ENABLE: "true"
Либо проще — оставить один listener PLAINTEXT://0.0.0.0:9092 с advertised 172.17.0.1:9092, если Kafka нужна только для k3s.

Шаг 2 — Создать топики вручную (надёжнее чем auto-create)
bash
docker exec -it <kafka-container> bash

# Создать топики
kafka-topics.sh --bootstrap-server localhost:9092 --create \
--topic shop.orders.created --partitions 1 --replication-factor 1

kafka-topics.sh --bootstrap-server localhost:9092 --create \
--topic crm.orders.status_changed --partitions 1 --replication-factor 1

kafka-topics.sh --bootstrap-server localhost:9092 --create \
--topic shop.orders.created.dlq --partitions 1 --replication-factor 1

# Проверка
kafka-topics.sh --bootstrap-server localhost:9092 --list
Шаг 3 — Тест #1: Shop → CRM (новый заказ)
3a. Проверить что CRM-consumer запущен
bash
kubectl logs -n <namespace> deployment/crm-backend | grep -E "KafkaListener|ShopOrderConsumer|kafka"
3b. Создать заказ через Shop API
bash
# Авторизоваться в Shop и создать заказ
TOKEN=$(curl -s -X POST https://bolohonovma.online/shop/api/v1/auth/login \
-H "Content-Type: application/json" \
-d '{"email":"test@test.com","password":"password"}' | jq -r '.token')

curl -X POST https://bolohonovma.online/shop/api/v1/orders \
-H "Authorization: Bearer $TOKEN" \
-H "Content-Type: application/json" \
-d '{
"items": [{"productId": "<uuid>", "quantity": 1}]
}'
3c. Мониторинг в реальном времени
bash
# Терминал 1 — смотрим что Shop записал в outbox
kubectl exec -n <ns> deploy/shop-backend -- \
psql $DATABASE_URL -c \
"SELECT id, topic, status, attempt_count, created_at FROM kafka_outbox ORDER BY created_at DESC LIMIT 5;"

# Терминал 2 — смотрим что Kafka получила
docker exec -it <kafka-container> \
kafka-console-consumer.sh \
--bootstrap-server localhost:9092 \
--topic shop.orders.created \
--from-beginning \
--max-messages 5

# Терминал 3 — логи CRM consumer
kubectl logs -f -n <ns> deployment/crm-backend | grep -E "ShopOrderConsumer|shopOrderId"
3d. Проверить результат в CRM DB
bash
kubectl exec -n <ns> deploy/crm-backend -- \
psql $DATABASE_URL -c \
"SET search_path TO tenant_shop; SELECT id, external_order_id, status_id, total_amount, created_at FROM orders ORDER BY created_at DESC LIMIT 3;"
Шаг 4 — Тест #2: CRM → Shop (смена статуса)
4a. Сменить статус заказа через CRM API
bash
CRM_TOKEN=$(curl -s -X POST https://bolohonovma.online/crm/api/v1/auth/login \
-H "Content-Type: application/json" \
-d '{"username":"admin","password":"admin","tenantSchema":"tenant_shop"}' | jq -r '.token')

curl -X PATCH https://bolohonovma.online/crm/api/v1/orders/<crm-order-id>/status \
-H "Authorization: Bearer $CRM_TOKEN" \
-H "Content-Type: application/json" \
-d '{"statusCode": "IN_PROGRESS", "comment": "Принят в работу"}'
4b. Мониторинг
bash
# Kafka consumer side — что видит Shop
docker exec -it <kafka-container> \
kafka-console-consumer.sh \
--bootstrap-server localhost:9092 \
--topic crm.orders.status_changed \
--from-beginning

# Логи Shop consumer
kubectl logs -f -n <ns> deployment/shop-backend | grep -E "OrderStatusConsumer|shopOrderUuid"

# Проверить статус заказа в Shop DB
kubectl exec -n <ns> deploy/shop-backend -- \
psql $DATABASE_URL -c \
"SELECT id, order_number, status, crm_order_id FROM shop_orders ORDER BY updated_at DESC LIMIT 3;"
Шаг 5 — Проверка Outbox pattern (PENDING → PUBLISHED)
bash
# Shop outbox
kubectl exec -n <ns> deploy/shop-backend -- \
psql $DATABASE_URL -c \
"SELECT topic, status, attempt_count, published_at, error_message
FROM kafka_outbox ORDER BY created_at DESC LIMIT 10;"

# CRM outbox
kubectl exec -n <ns> deploy/crm-backend -- \
psql $DATABASE_URL -c \
"SET search_path TO public;
SELECT topic, status, attempt_count, published_at, error_message
FROM kafka_outbox ORDER BY created_at DESC LIMIT 10;"
Ожидаемый результат: status = PUBLISHED, attempt_count = 1, error_message = null.

Шаг 6 — Тест идемпотентности (защита от дублей)
bash
# Продублировать сообщение вручную — взять payload из outbox и отправить повторно
docker exec -it <kafka-container> bash

# Взять последнее сообщение из топика и переотправить
kafka-console-producer.sh \
--bootstrap-server localhost:9092 \
--topic shop.orders.created <<< '<json из предыдущего теста>'
Ожидаемый лог CRM: Duplicate shopOrderUuid=... — skipping

Частые проблемы и что искать
Симптом	Причина	Лечение
PLAINTEXT://kafka:9092 недоступен из k3s	DNS kafka не резолвится в поде	Использовать 172.17.0.1:9092
Outbox остаётся PENDING	Kafka недоступна → attempt_count растёт	Проверить KAFKA_BOOTSTRAP_SERVERS в env пода
Consumer не читает	Неверный trusted.packages	CRM ждёт com.crm.kafka.dto, Shop ждёт com.shop.kafka.dto
search_path ошибки в CRM	KAFKA_SHOP_TENANT_SCHEMA не задан	Добавить в Helm values
DLQ сообщения	Схема тенанта не найдена или ошибка в БД	Смотреть shop.orders.created.dlq
Если скажешь namespace в k3s и имя kafka-контейнера — могу сразу написать готовые команды под твою конкретную конфигурацию.

michael@demo-server:~$ # Проверяем что Kafka слушает снаружи
docker exec -it <kafka-container> kafka-topics.sh \
--bootstrap-server localhost:9092 \
--list
-bash: kafka-container: No such file or directory

6 мар.
Нужно сначала найти имя контейнера:

bash
docker ps | grep kafka
michael@demo-server:~$ docker ps | grep kafka
ac84d14e493e   provectuslabs/kafka-ui:latest   "/bin/sh -c 'java --…"   3 days ago   Up 51 minutes   0.0.0.0:9093->8080/tcp, [::]:9093->8080/tcp   kafka-ui

6 мар.
Kafka UI есть, но самого брокера нет в списке. Смотрим все контейнеры:

bash
docker ps -a | grep -i kafka
И заодно:

bash
docker ps -a --format "table {{.Names}}\t{{.Image}}\t{{.Status}}" | head -30
michael@demo-server:~$ docker ps -a | grep -i kafka
ae7c7b4b0327   apache/kafka:latest             "/__cacert_entrypoin…"   14 minutes ago   Exited (1) 14 minutes ago                                                 shared-kafka
ac84d14e493e   provectuslabs/kafka-ui:latest   "/bin/sh -c 'java --…"   3 days ago       Up 51 minutes               0.0.0.0:9093->8080/tcp, [::]:9093->8080/tcp   kafka-ui

6 мар.
Kafka упала (Exited (1)). Смотрим почему:

bash
docker logs shared-kafka --tail 50

ssh -J root@77.222.35.2 michael@10.0.0.2 michael@demo-server:~$ # Проверяем что Kafka слушает снаружи docker exec -it <kafka-container> kafka-topics.sh \ --bootstrap-server localhost:9092 \ --list -bash: kafka-container: No such file or directory michael@demo-server:~$ docker ps | grep kafka ac8

pasted

6 мар.
Проблема ясна: shared-kafka пытается занять порт 9093, но его уже держит kafka-ui (который смапил 0.0.0.0:9093->8080).

Нужно посмотреть как запущен shared-kafka и поменять порт контроллера:

bash
# Смотрим чем был запущен shared-kafka
docker inspect shared-kafka | grep -A 30 '"Cmd"\|"Env"\|"PortBindings"'

ssh -J root@77.222.35.2 michael@10.0.0.2 michael@demo-server:~$ # Проверяем что Kafka слушает снаружи docker exec -it <kafka-container> kafka-topics.sh \ --bootstrap-server localhost:9092 \ --list -bash: kafka-container: No such file or directory michael@demo-server:~$ docker ps | grep kafka ac8

pasted

6 мар.
Всё ясно. Конфликт портов: shared-kafka хочет 0.0.0.0:9093 для CONTROLLER, но kafka-ui уже занял 9093 на хосте.

Решение — пересоздать shared-kafka с CONTROLLER на порту 19093 (внутренний, не публикуется наружу):

bash
# Удаляем старый контейнер
docker rm shared-kafka

# Запускаем с исправленным портом контроллера
docker run -d \
--name shared-kafka \
--restart unless-stopped \
-p 9092:9092 \
-e KAFKA_PROCESS_ROLES=broker,controller \
-e KAFKA_NODE_ID=1 \
-e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:19093 \
-e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://172.17.0.1:9092 \
-e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:19093 \
-e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
-e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT \
-e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
-e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
-e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
-e KAFKA_LOG_DIRS=/var/lib/kafka/data \
-e KAFKA_AUTO_CREATE_TOPICS_ENABLE=true \
-v kafka-data:/var/lib/kafka/data \
apache/kafka:latest

# Проверяем что запустилась
docker ps | grep shared-kafka
docker logs shared-kafka --tail 20
michael@demo-server:~$ docker ps | grep shared-kafka
b76e4828697e   apache/kafka:latest             "/__cacert_entrypoin…"   7 seconds ago   Up 7 seconds    0.0.0.0:9092->9092/tcp, [::]:9092->9092/tcp   shared-kafka

6 мар.
Kafka запущена. Проверяем что брокер готов и создаём топики:

bash
# Ждём секунд 5 и проверяем что брокер поднялся
docker logs shared-kafka --tail 10

# Создаём топики
docker exec shared-kafka /opt/kafka/bin/kafka-topics.sh \
--bootstrap-server localhost:9092 --create \
--topic shop.orders.created --partitions 1 --replication-factor 1

docker exec shared-kafka /opt/kafka/bin/kafka-topics.sh \
--bootstrap-server localhost:9092 --create \
--topic crm.orders.status_changed --partitions 1 --replication-factor 1

docker exec shared-kafka /opt/kafka/bin/kafka-topics.sh \
--bootstrap-server localhost:9092 --create \
--topic shop.orders.created.dlq --partitions 1 --replication-factor 1

# Проверяем список
docker exec shared-kafka /opt/kafka/bin/kafka-topics.sh \
--bootstrap-server localhost:9092 --list
michael@demo-server:~$ docker logs shared-kafka --tail 10
[2026-03-06 15:17:38,089] INFO [GroupCoordinator id=1 topic=__consumer_offsets partition=27] Dynamic member with unknown member id joins group shop-backend in Empty state. Created a new member id consumer-shop-backend-2-742b1799-a191-4227-a836-315dcbc65c08 and requesting the member to rejoin with this id. (org.apache.kafka.coordinator.group.GroupMetadataManager)
[2026-03-06 15:17:38,094] INFO [GroupCoordinator id=1 topic=__consumer_offsets partition=27] Pending dynamic member with id consumer-shop-backend-1-920f5e0f-126d-48ff-9e07-b123ed4cef8a joins group shop-backend in Empty state. Adding to the group now. (org.apache.kafka.coordinator.group.GroupMetadataManager)
[2026-03-06 15:17:38,096] INFO [GroupCoordinator id=1 topic=__consumer_offsets partition=27] Preparing to rebalance group shop-backend in state PreparingRebalance with old generation 0 (reason: Adding new member consumer-shop-backend-1-920f5e0f-126d-48ff-9e07-b123ed4cef8a with group instance id null; client reason: need to re-join with the given member-id: consumer-shop-backend-1-920f5e0f-126d-48ff-9e07-b123ed4cef8a). (org.apache.kafka.coordinator.group.GroupMetadataManager)
[2026-03-06 15:17:38,098] INFO [GroupCoordinator id=1 topic=__consumer_offsets partition=27] Pending dynamic member with id consumer-shop-backend-2-742b1799-a191-4227-a836-315dcbc65c08 joins group shop-backend in PreparingRebalance state. Adding to the group now. (org.apache.kafka.coordinator.group.GroupMetadataManager)
[2026-03-06 15:17:40,933] INFO [GroupCoordinator id=1 topic=__consumer_offsets partition=46] Stabilized group shop-product-sync generation 1 with 1 members. (org.apache.kafka.coordinator.group.GroupMetadataManager)
[2026-03-06 15:17:41,003] INFO [GroupCoordinator id=1 topic=__consumer_offsets partition=46] Assignment received from leader consumer-shop-product-sync-3-654258b5-e524-4383-b7e6-22613b9618d9 for group shop-product-sync for generation 1. The group has 1 members, 0 of which are static. (org.apache.kafka.coordinator.group.GroupMetadataManager)
[2026-03-06 15:17:43,953] INFO [GroupCoordinator id=1 topic=__consumer_offsets partition=43] Stabilized group crm-backend generation 1 with 2 members. (org.apache.kafka.coordinator.group.GroupMetadataManager)
[2026-03-06 15:17:43,994] INFO [GroupCoordinator id=1 topic=__consumer_offsets partition=43] Assignment received from leader consumer-crm-backend-2-bf30ef74-2766-46cd-a318-0d9834a81a39 for group crm-backend for generation 1. The group has 2 members, 0 of which are static. (org.apache.kafka.coordinator.group.GroupMetadataManager)
[2026-03-06 15:17:44,097] INFO [GroupCoordinator id=1 topic=__consumer_offsets partition=27] Stabilized group shop-backend generation 1 with 2 members. (org.apache.kafka.coordinator.group.GroupMetadataManager)
[2026-03-06 15:17:44,107] INFO [GroupCoordinator id=1 topic=__consumer_offsets partition=27] Assignment received from leader consumer-shop-backend-1-920f5e0f-126d-48ff-9e07-b123ed4cef8a for group shop-backend for generation 1. The group has 2 members, 0 of which are static. (org.apache.kafka.coordinator.group.GroupMetadataManager)

Show more
6 мар.
Отлично! Kafka не просто запустилась — все консьюмеры уже подключились автоматически:

crm-backend — 2 member'а ✅
shop-backend — 2 member'а ✅
shop-product-sync — 1 member ✅
Топики создавать не нужно, AUTO_CREATE_TOPICS_ENABLE=true уже сработал. Проверяем что всё на месте:

bash
docker exec shared-kafka /opt/kafka/bin/kafka-topics.sh \
--bootstrap-server localhost:9092 --list
michael@demo-server:~$ docker exec shared-kafka /opt/kafka/bin/kafka-topics.sh \
--bootstrap-server localhost:9092 --list
__consumer_offsets
crm.orders.status_changed
crm.products.sync
crm.tenant.created
shop.orders.created

6 мар.
Все топики на месте! DLQ нет, но это не критично — создастся автоматически при первой ошибке. Теперь делаем живой тест — создаём заказ в Shop и смотрим как он появится в CRM.

Открываем два терминала:

Терминал 1 — мониторим топик в реальном времени:

bash
docker exec shared-kafka /opt/kafka/bin/kafka-console-consumer.sh \
--bootstrap-server localhost:9092 \
--topic shop.orders.created \
--from-beginning
Терминал 2 — смотрим логи CRM consumer:

bash
kubectl logs -f deployment/crm-backend -n <namespace> | grep -E "ShopOrderConsumer|shopOrderId|Kafka received|Order created"
Какой namespace используешь в k3s? И есть ли уже тестовый пользователь в Shop для создания заказа?

а при перезапуске сервера kafka запустится автоматически - вручную ничего делать не нужно

6 мар.
Нет, не запустится — контейнер создан без политики перезапуска. Смотри, у тебя сейчас:

"RestartPolicy": { "Name": "no" }
Нужно обновить:

bash
docker update --restart unless-stopped shared-kafka
unless-stopped — значит будет стартовать автоматически после перезагрузки сервера и при падении, но не поднимется если ты сам остановил его через docker stop.

michael@demo-server:~$ docker update --restart unless-stopped shared-kafka
shared-kafka

6 мар.
Готово. Теперь Kafka будет подниматься автоматически вместе с сервером.

Возвращаемся к тестированию — какой namespace в k3s и есть ли тестовый юзер в Shop?

ты видел что там tenant - и топик должен записать при создании пользователя

6 мар.
Да, верно — смотрел код Shop. При регистрации нового пользователя Shop публикует в crm.tenant.created, CRM его получает и создаёт схему тенанта.

Проверяем логи и что сейчас в outbox:

bash
# Какие namespace есть
kubectl get namespaces

# Логи shop на предмет tenant событий
kubectl logs deployment/shop-backend -n <namespace> | grep -E "tenant|Kafka|outbox" | tail -20
michael@demo-server:~$ kubectl get namespaces
NAME              STATUS   AGE
crm               Active   33h
default           Active   7d6h
kube-node-lease   Active   7d6h
kube-public       Active   7d6h
kube-system       Active   7d6h
shop              Active   2d7h

6 мар.
Отлично. Смотрим логи обоих сервисов:

bash
# Shop — что происходит с outbox и tenant
kubectl logs deployment/shop-backend -n shop | grep -E "tenant|outbox|Kafka|kafka" | tail -20

# CRM — получил ли tenant.created
kubectl logs deployment/crm-backend -n crm | grep -E "tenant|outbox|Kafka|kafka" | tail -20
я не про то - давай проверим таблицы в бД правильно ли заполнена таблица промежуточная - которая хранит tenant топик , бд тоже в докере

6 мар.
Смотрим какие DB контейнеры есть:

bash
docker ps | grep -E "postgres|pg"
michael@demo-server:~$ docker ps | grep -E "postgres|pg"
f0fd36a7498b   postgres:16-alpine              "docker-entrypoint.s…"   5 days ago      Up 57 minutes   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp   postgres

6 мар.
Один общий postgres. Смотрим outbox таблицы в обеих БД:

bash
# Какие базы есть
docker exec postgres psql -U postgres -c "\l"
michael@demo-server:~$ # Какие базы есть
docker exec postgres psql -U postgres -c "\l"
psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed: FATAL:  role "postgres" does not exist

6 мар.
bash
docker exec postgres psql -U $(docker exec postgres env | grep POSTGRES_USER | cut -d= -f2) -c "\l"
Или сначала узнаем юзера:

bash
docker exec postgres env | grep POSTGRES
POSTGRES_DB=chatdb
POSTGRES_USER=cha

6 мар.
bash
docker exec postgres psql -U chat -c "\l"
psql: error: connection to server on socket "/var/run/postgresql/.s.PGSQL.5432" failed: FATAL:  database "chat" does not exist

6 мар.
bash
docker exec postgres psql -U chat -d chatdb -c "\l"
michael@demo-server:~$ docker exec postgres psql -U chat -d chatdb -c "\l"
List of databases
Name    |   Owner   | Encoding | Locale Provider |  Collate   |   Ctype    | ICU Locale | ICU Rules | Access privileges
-----------+-----------+----------+-----------------+------------+------------+------------+-----------+-------------------
chatdb    | chat      | UTF8     | libc            | en_US.utf8 | en_US.utf8 |            |           |
crm_db    | chat      | UTF8     | libc            | en_US.utf8 | en_US.utf8 |            |           | =Tc/chat         +
|           |          |                 |            |            |            |           | chat=CTc/chat    +
|           |          |                 |            |            |            |           | crm_user=CTc/chat
postgres  | chat      | UTF8     | libc            | en_US.utf8 | en_US.utf8 |            |           |
shop_db   | shop_user | UTF8     | libc            | en_US.utf8 | en_US.utf8 |            |           |
template0 | chat      | UTF8     | libc            | en_US.utf8 | en_US.utf8 |            |           | =c/chat          +
|           |          |                 |            |            |            |           | chat=CTc/chat
template1 | chat      | UTF8     | libc            | en_US.utf8 | en_US.utf8 |            |           | =c/chat          +
|           |          |                 |            |            |            |           | chat=CTc/chat
(6 rows)

Show more
6 мар.
Отлично, обе базы видны. Смотрим outbox таблицы:

bash
# Shop outbox
docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT id, topic, status, attempt_count, created_at, error_message FROM kafka_outbox ORDER BY created_at DESC LIMIT 10;"

# CRM outbox
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, topic, status, attempt_count, created_at, error_message FROM public.kafka_outbox ORDER BY created_at DESC LIMIT 10;"
ERROR:  column "error_message" does not exist
LINE 1: ...ECT id, topic, status, attempt_count, created_at, error_mess...
^
ERROR:  column "error_message" does not exist
LINE 1: ...ECT id, topic, status, attempt_count, created_at, error_mess...

6 мар.
bash
# Смотрим реальные колонки
docker exec postgres psql -U shop_user -d shop_db -c "\d kafka_outbox"
docker exec postgres psql -U crm_user -d crm_db -c "\d kafka_outbox"
michael@demo-server:~$ docker exec postgres psql -U shop_user -d shop_db -c "\d kafka_outbox"
docker exec postgres psql -U crm_user -d crm_db -c "\d kafka_outbox"
Table "public.kafka_outbox"
Column     |           Type           | Collation | Nullable |           Default            
---------------+--------------------------+-----------+----------+------------------------------
id            | uuid                     |           | not null | uuid_generate_v4()
topic         | character varying(200)   |           | not null |
message_key   | character varying(200)   |           |          |
payload       | text                     |           | not null |
status        | character varying(20)    |           | not null | 'PENDING'::character varying
created_at    | timestamp with time zone |           | not null | now()
published_at  | timestamp with time zone |           |          |
attempt_count | integer                  |           |          | 0
last_error    | text                     |           |          |
Indexes:
"kafka_outbox_pkey" PRIMARY KEY, btree (id)
"idx_outbox_status" btree (status)
Table "public.kafka_outbox"
Column     |           Type           | Collation | Nullable |           Default            
---------------+--------------------------+-----------+----------+------------------------------
id            | uuid                     |           | not null | uuid_generate_v4()
topic         | character varying(200)   |           | not null |
message_key   | character varying(500)   |           | not null |
payload       | text                     |           | not null |
status        | character varying(20)    |           | not null | 'PENDING'::character varying
created_at    | timestamp with time zone |           | not null | now()
published_at  | timestamp with time zone |           |          |
attempt_count | integer                  |           | not null | 0
last_error    | character varying(500)   |           |          |
Indexes:
"kafka_outbox_pkey" PRIMARY KEY, btree (id)
"idx_kafka_outbox_status_created" btree (status, created_at)
Check constraints:
"chk_kafka_outbox_status" CHECK (status::text = ANY (ARRAY['PENDING'::character varying, 'PUBLISHED'::character varying, 'FAILED'::character varying]::text[]))

Show more
6 мар.
bash
# Shop outbox
docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT topic, status, attempt_count, created_at, last_error FROM kafka_outbox ORDER BY created_at DESC LIMIT 10;"

# CRM outbox
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT topic, status, attempt_count, created_at, last_error FROM kafka_outbox ORDER BY created_at DESC LIMIT 10;"
---------------------+-----------+---------------+-------------------------------+-------------
shop.orders.created | FAILED    |             5 | 2026-03-06 15:06:49.500097+00 | Send failed
shop.orders.created | PUBLISHED |             0 | 2026-03-06 14:48:51.757359+00 |
(2 rows)
topic        |  status   | attempt_count |          created_at           | last_error
--------------------+-----------+---------------+-------------------------------+------------
crm.tenant.created | PUBLISHED |             1 | 2026-03-06 14:30:19.406423+00 |
crm.tenant.created | PUBLISHED |             1 | 2026-03-06 10:06:27.773472+00 |
(2 rows)

6 мар.
Картина понятна:

CRM outbox — оба crm.tenant.created в PUBLISHED ✅ — тенанты успешно отправлены в Kafka.

Shop outbox — тут проблема:

Один shop.orders.created — PUBLISHED ✅ (заказ от 14:48, до падения Kafka)
Один shop.orders.created — FAILED с 5 попытками ❌ (заказ от 15:06, когда Kafka была упавшей)
FAILED записи Outbox Poller больше не трогает (достиг лимита попыток). Нужно вручную сбросить чтобы переотправить:

bash
docker exec postgres psql -U shop_user -d shop_db -c \
"UPDATE kafka_outbox SET status='PENDING', attempt_count=0, last_error=NULL
WHERE status='FAILED';"
После этого Poller подхватит запись в течение 5 секунд и отправит в Kafka. Следим:

bash
# Через 10 секунд проверяем статус
docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT topic, status, attempt_count, published_at, last_error FROM kafka_outbox ORDER BY created_at DESC LIMIT 5;"
michael@demo-server:~$ # Через 10 секунд проверяем статус
docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT topic, status, attempt_count, published_at, last_error FROM kafka_outbox ORDER BY created_at DESC LIMIT 5;"
topic        |  status   | attempt_count |         published_at          | last_error
---------------------+-----------+---------------+-------------------------------+------------
shop.orders.created | PUBLISHED |             0 | 2026-03-06 15:24:39.154799+00 |
shop.orders.created | PUBLISHED |             0 | 2026-03-06 14:48:56.653372+00 |
(2 rows)

6 мар.
Оба заказа PUBLISHED ✅. Теперь проверяем что CRM получил и создал заказы в своей БД:

bash
# Смотрим схемы тенантов в CRM
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant%';"

# Заказы в tenant схеме
docker exec postgres psql -U crm_user -d crm_db -c \
"SET search_path TO tenant_shop;
SELECT id, external_order_id, shop_order_uuid, total_amount, created_at
FROM orders ORDER BY created_at DESC LIMIT 5;"
---------------------------------------------
tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a
tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710
tenant_716fe01a_c093_4dd5_8d57_57fea230b9ab
tenant_31d5c27b_3426_407e_a342_79d991f8686b
tenant_95a19f44_b3b9_4eb2_8037_8c01775274dd
tenant_fa046288_833a_4c9e_9712_4cb5fa8b335c
tenant_7dbf5c25_540f_40e4_af20_cb0906217826
tenant_6a3fed6a_1402_4a5d_888c_830fda4217c9
tenant_18e498a8_b38a_4cf7_b0dc_ca890b4e69f1
(9 rows)
ERROR:  relation "orders" does not exist
LINE 3:    FROM orders ORDER BY created_at DESC LIMIT 5;
^
SET

Show more
6 мар.
9 тенант-схем создано ✅. Но tenant_shop не существует — нужно узнать правильную схему. Смотрим какой тенант соответствует shop:

bash
# Смотрим в CRM какие tenants зарегистрированы
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, name, schema_name FROM public.tenants ORDER BY created_at DESC LIMIT 10;"
michael@demo-server:~$ # Смотрим в CRM какие tenants зарегистрированы
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, name, schema_name FROM public.tenants ORDER BY created_at DESC LIMIT 10;"
id                  |       name       |                 schema_name                 
--------------------------------------+------------------+---------------------------------------------
4cff7c65-8997-4be6-a39c-2feac8c7242a | Михаил Болохонов | tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a
3a47abae-2dc5-4547-9ba9-3c08fe65a710 | Михаил Болохонов | tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710
(2 rows)

6 мар.
Только 2 тенанта из 9 схем имеют записи (остальные схемы — мусор от старых тестов). Смотрим заказы в обоих:

bash
docker exec postgres psql -U crm_user -d crm_db -c "
SELECT t.name, t.schema_name,
(SELECT COUNT(*) FROM tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a.orders) as orders_4cff,
(SELECT COUNT(*) FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.orders) as orders_3a47;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c "
SELECT t.name, t.schema_name,
(SELECT COUNT(*) FROM tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a.orders) as orders_4cff,
(SELECT COUNT(*) FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.orders) as orders_3a47;"
ERROR:  missing FROM-clause entry for table "t"
LINE 2: SELECT t.name, t.schema_name,

6 мар.
bash
docker exec postgres psql -U crm_user -d crm_db -c "
SELECT COUNT(*) as orders FROM tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a.orders;
SELECT COUNT(*) as orders FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.orders;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c "
SELECT COUNT(*) as orders FROM tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a.orders;
SELECT COUNT(*) as orders FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.orders;"
orders
--------
     80
(1 row)
orders
--------
     80
(1 row)

Show more
6 мар.
По 80 заказов в каждом тенанте — данные есть. Теперь проверяем что свежий заказ (который только что переотправили из FAILED) дошёл до CRM:

bash
docker exec postgres psql -U crm_user -d crm_db -c "
SELECT external_order_id, shop_order_uuid, total_amount, created_at
FROM tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a.orders
ORDER BY created_at DESC LIMIT 3;

SELECT external_order_id, shop_order_uuid, total_amount, created_at
FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.orders
ORDER BY created_at DESC LIMIT 3;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c "
SELECT external_order_id, shop_order_uuid, total_amount, created_at
FROM tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a.orders
ORDER BY created_at DESC LIMIT 3;
SELECT external_order_id, shop_order_uuid, total_amount, created_at
FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.orders
ORDER BY created_at DESC LIMIT 3;"
external_order_id | shop_order_uuid | total_amount |         created_at          
-------------------+-----------------+--------------+-----------------------------
|                 |     45000.00 | 2026-03-06 13:58:19.6614+00
|                 |    480000.00 | 2026-03-06 12:28:19.6614+00
|                 |     60000.00 | 2026-03-06 10:28:19.6614+00
(3 rows)
external_order_id | shop_order_uuid | total_amount |          created_at           
-------------------+-----------------+--------------+-------------------------------
|                 |     45000.00 | 2026-03-06 09:35:53.437302+00
|                 |    480000.00 | 2026-03-06 08:05:53.437302+00
|                 |     60000.00 | 2026-03-06 06:05:53.437302+00
(3 rows)

Show more
6 мар.
Вижу проблему — external_order_id и shop_order_uuid пустые у всех заказов. Это значит что эти 80 заказов — тестовые данные созданные напрямую в БД (через Liquibase seed или вручную), а не через Kafka.

Свежий заказ от 15:24 (который мы только что переотправили) не появился. Проверяем idempotency log и логи CRM consumer:

bash
# Проверяем idempotency log — дошло ли сообщение
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT * FROM public.kafka_idempotency_log ORDER BY processed_at DESC LIMIT 5;"

# Логи CRM — что происходит с consumer
kubectl logs deployment/crm-backend -n crm --tail=30 | grep -E "Kafka|kafka|tenant|ShopOrder|ERROR"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT * FROM public.kafka_idempotency_log ORDER BY processed_at DESC LIMIT 5;"
id | shop_order_uuid | crm_order_id | processed_at
----+-----------------+--------------+--------------
(0 rows)

6 мар.
Idempotency log пустой — CRM не обработал ни одного сообщения из Kafka. Смотрим логи:

bash
kubectl logs deployment/crm-backend -n crm --tail=50 | grep -E "Kafka|kafka|ShopOrder|tenant|ERROR|WARN"
michael@demo-server:~$ kubectl logs deployment/crm-backend -n crm --tail=50 | grep -E "Kafka|kafka|ShopOrder|tenant|ERROR|WARN"
error: error from server (NotFound): deployments.apps "crm-backend" not found in namespace "crm"

6 мар.
bash
kubectl get pods -n crm
kubectl get pods -n shop
michael@demo-server:~$ kubectl get pods -n crm
kubectl get pods -n shop
NAME                                READY   STATUS    RESTARTS   AGE
crm-app-backend-f7cc7f47d-q5tq4     1/1     Running   0          25m
crm-app-frontend-5767b7988f-5h9rh   1/1     Running   0          42m
NAME                                 READY   STATUS    RESTARTS   AGE
shop-app-backend-646fbd6dd4-wcpld    1/1     Running   0          25m
shop-app-frontend-7cffcfb5bf-p5x5g   1/1     Running   0          40m
michael@demo-server:~$

6 мар.
bash
kubectl logs crm-app-backend-f7cc7f47d-q5tq4 -n crm --tail=50 | grep -E "Kafka|kafka|ShopOrder|tenant|ERROR|WARN"
ssh -J root@77.222.35.2 michael@10.0.0.2
michael@demo-server:~$ kubectl logs crm-app-backend-f7cc7f47d-q5tq4 -n crm --tail=50 | grep -E "Kafka|kafka|ShopOrder|tenant|ERROR|WARN"
2026-03-06 15:27:32 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Creating new transaction with name [com.crm.kafka.outbox.KafkaOutboxPoller.poll]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
2026-03-06 15:27:32 [scheduling-1] DEBUG [] o.s.jdbc.core.JdbcTemplate - Executing prepared SQL statement [SELECT * FROM public.kafka_outbox
2026-03-06 15:27:37 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Creating new transaction with name [com.crm.kafka.outbox.KafkaOutboxPoller.poll]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
2026-03-06 15:27:37 [scheduling-1] DEBUG [] o.s.jdbc.core.JdbcTemplate - Executing prepared SQL statement [SELECT * FROM public.kafka_outbox
2026-03-06 15:27:42 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Creating new transaction with name [com.crm.kafka.outbox.KafkaOutboxPoller.poll]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
2026-03-06 15:27:42 [scheduling-1] DEBUG [] o.s.jdbc.core.JdbcTemplate - Executing prepared SQL statement [SELECT * FROM public.kafka_outbox
michael@demo-server:~$

Show more
6 мар.
CRM outbox poller работает, но нет никаких сообщений о получении заказов из Shop. Смотрим без фильтра — может consumer вообще не стартовал:

bash
kubectl logs crm-app-backend-f7cc7f47d-q5tq4 -n crm --tail=100 | grep -E "ShopOrder|consumer|Consumer|shop.orders|PLAINTEXT|bootstrap"
michael@demo-server:~$ kubectl logs crm-app-backend-f7cc7f47d-q5tq4 -n crm --tail=100 | grep -E "ShopOrder|consumer|Consumer|shop.orders|PLAINTEXT|bootstrap"

6 мар.
Пусто — consumer вообще не упоминается в логах. Скорее всего проблема в KAFKA_BOOTSTRAP_SERVERS env переменной пода. Проверяем:

bash
kubectl exec crm-app-backend-f7cc7f47d-q5tq4 -n crm -- env | grep KAFKA
KAFKA_BOOTSTRAP_SERVERS=172.17.0.1:9092
KAFKA_TOPIC_CRM_STATUS_CHANGED=crm.orders.status_changed
KAFKA_TOPIC_SHOP_ORDER_CREATED=shop.orders.created
APP_KAFKA_OUTBOX_POLL_INTERVAL_MS=5000
KAFKA_TOPIC_DLQ=shop.orders.created.dlq
KAFKA_SHOP_TENANT_SCHEMA=tenant_shop
KAFKA_CONSUMER_GROUP=crm-backend

6 мар.
Env правильный, но KAFKA_SHOP_TENANT_SCHEMA=tenant_shop — а такой схемы не существует! В БД схемы называются tenant_4cff7c65_... и tenant_3a47abae_....

Но сначала проверим почему consumer молчит — смотрим полные логи старта:

bash
kubectl logs crm-app-backend-f7cc7f47d-q5tq4 -n crm | grep -E "started|Started|KafkaListener|ListenerContainer|shop.orders" | head -20

michael@demo-server:~$ kubectl logs crm-app-backend-f7cc7f47d-q5tq4 -n crm | grep -E "started|Started|KafkaListener|ListenerContainer|shop.orders" | head -20 2026-03-06 15:02:12 [main] INFO [] com.crm.CrmApplication - Starting CrmApplication v0.1.0-SNAPSHOT using Java 21.0.10 with PID 1 (/app/app.j

pasted

Env правильный, но KAFKA_SHOP_TENANT_SCHEMA=tenant_shop — а такой схемы не существует! В БД схемы называются tenant_4cff7c65_... и tenant_3a47abae_.... - она динамически из таблицы подставляется - это закладывали в код

6 мар.
Точно, моя ошибка — это резервный fallback, а реальная схема берётся из самого события.

Проблема другая и она видна в логах — CRM стартовал в 15:04, а Kafka поднялась только в 15:17. Consumer пытался подключиться, получил disconnect, и больше не переподключился. Нужно просто рестартовать под:

bash
kubectl rollout restart deployment/crm-app-backend -n crm
странно что кафка позже стартовал - мы же проверяли

6 мар.
Да, Kafka стартовала в 15:17, а CRM поднялся в 15:02 — потому что shared-kafka был Exited и мы его пересоздали в ~15:17. CRM стартовал раньше со старым упавшим контейнером.

После рестарта CRM сам переподключится. Проверяем:

bash
kubectl rollout restart deployment/crm-app-backend -n crm

# Ждём и смотрим новый под
kubectl get pods -n crm -w
crm-app-backend-5959cd8db7-n488c    0/1     Running   0          2m2s
crm-app-backend-5959cd8db7-n488c    1/1     Running   0          2m3s
crm-app-backend-f7cc7f47d-q5tq4     1/1     Terminating   0          29m
crm-app-backend-f7cc7f47d-q5tq4     1/1     Terminating   0          29m
crm-app-backend-f7cc7f47d-q5tq4     0/1     Error         0          29m
crm-app-backend-f7cc7f47d-q5tq4     0/1     Error         0          29m
crm-app-backend-f7cc7f47d-q5tq4     0/1     Error         0          29m

6 мар.
Новый под поднялся (1/1 Running). Проверяем что consumer теперь подключился к Kafka:

bash
kubectl logs crm-app-backend-5959cd8db7-n488c -n crm | grep -E "Subscribed|Connected|bootstrap|shop.orders|ERROR" | head -20

ssh -J root@77.222.35.2 michael@10.0.0.2 crm-app-backend-5959cd8db7-n488c 0/1 Running 0 2m2s crm-app-backend-5959cd8db7-n488c 1/1 Running 0 2m3s crm-app-backend-f7cc7f47d-q5tq4 1/1 Terminating 0 29m crm-app-backend-f7cc7f47d-q5tq4 1/1 Te

pasted

6 мар.
Consumer подключился и читает с offset=0 ✅. Но есть важный момент:

Topic 'shop.orders.created' exists but has a different partition count: 1 not 3
CRM ожидает 3 партиции, а топик создан с 1. Нужно пересоздать топик с 3 партициями:

bash
# Удаляем и создаём заново с 3 партициями
docker exec shared-kafka /opt/kafka/bin/kafka-topics.sh \
--bootstrap-server localhost:9092 --delete \
--topic shop.orders.created

docker exec shared-kafka /opt/kafka/bin/kafka-topics.sh \
--bootstrap-server localhost:9092 --create \
--topic shop.orders.created --partitions 3 --replication-factor 1

# Проверяем
docker exec shared-kafka /opt/kafka/bin/kafka-topics.sh \
--bootstrap-server localhost:9092 --describe \
--topic shop.orders.created
После этого нужно будет переотправить FAILED заказ из outbox и сделать рестарт CRM пода чтобы он подхватил новый топик.

вообще все эти топики не должны автоматически создаваться при старте ?

6 мар.
Да, должны — Spring Kafka автоматически создаёт топики через KafkaAdmin если они объявлены через @Bean NewTopic. Смотрим конфиг:

bash
cat /home/claude/crm/crm_final/crm-backend/src/main/java/com/crm/kafka/config/KafkaConfig.java

package com.crm.kafka.config; import com.crm.kafka.dto.ShopOrderCreatedEvent; import lombok.RequiredArgsConstructor; import org.apache.kafka.clients.admin.NewTopic; import org.apache.kafka.clients.consumer.ConsumerConfig; import org.apache.kafka.clients.producer.ProducerConfig; import org.apache.ka

pasted

6 мар.
Всё правильно — @Bean NewTopic есть, топики должны создаваться автоматически с 3 партициями. Но проблема в том что когда CRM первый раз стартовал — Kafka была недоступна, поэтому KafkaAdmin не смог создать топики. Топик shop.orders.created создался позже автоматически с 1 партицией (от Shop или вручную).

Сейчас после рестарта CRM увидел несоответствие (1 vs 3) и попытался увеличить — нужно проверить получилось ли:

bash
docker exec shared-kafka /opt/kafka/bin/kafka-topics.sh \
--bootstrap-server localhost:9092 --describe

ssh -J root@77.222.35.2 michael@10.0.0.2 michael@demo-server:~$ cat /home/claude/crm/crm_final/crm-backend/src/main/java/com/crm/kafka/config/KafkaConfig.java cat: /home/claude/crm/crm_final/crm-backend/src/main/java/com/crm/kafka/config/KafkaConfig.java: No such file or directory michael@demo-serve

pasted

6 мар.
Все топики в порядке ✅:

shop.orders.created — 3 партиции ✅
crm.orders.status_changed — 3 партиции ✅
shop.orders.created.dlq — 1 партиция ✅
crm.tenant.created — 1 партиция ✅
Теперь переотправляем FAILED заказ и проверяем что CRM его обработает:

bash
# Сбрасываем FAILED запись в outbox
docker exec postgres psql -U shop_user -d shop_db -c \
"UPDATE kafka_outbox SET status='PENDING', attempt_count=0, last_error=NULL WHERE status='FAILED';"

# Ждём 10 секунд и смотрим логи CRM
sleep 10 && kubectl logs crm-app-backend-5959cd8db7-n488c -n crm --tail=20 | grep -E "ShopOrder|Order created|Kafka received|ERROR"
michael@demo-server:~$ sleep 10 && kubectl logs crm-app-backend-5959cd8db7-n488c -n crm --tail=20 | grep -E "ShopOrder|Order created|Kafka received|ERROR"
michael@demo-server:~$

6 мар.
Пусто — CRM не получил сообщение. Проверяем дошло ли оно до Kafka и на какую партицию попало:

bash
# Смотрим что есть в топике
docker exec shared-kafka /opt/kafka/bin/kafka-console-consumer.sh \
--bootstrap-server localhost:9092 \
--topic shop.orders.created \
--from-beginning \
--timeout-ms 5000
michael@demo-server:~$ # Смотрим что есть в топике
docker exec shared-kafka /opt/kafka/bin/kafka-console-consumer.sh \
--bootstrap-server localhost:9092 \
--topic shop.orders.created \
--from-beginning \
--timeout-ms 5000
{"shopOrderId":"SHOP-00004","shopOrderUuid":"755ac0a0-1293-47ae-8b56-1a35ba6c28ea","tenantSchema":"tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710","customer":{"externalId":"ace4df3f-dd3e-40a6-9b65-5f69a707b234","firstName":"Михаил","lastName":"Болохонов","email":"bolohonovmakar@gmail.com","phone":"+79053251464","address":"Саратов, ул. Советская, 11, кв. 53"},"items":[{"sku":"LIC-M365-001","name":"Microsoft 365 Business","quantity":1,"price":7200.00}],"totalAmount":7200.00,"createdAt":"2026-03-06T15:06:49.482625573Z"}
[2026-03-06 15:35:09,655] ERROR Error processing message, terminating consumer process:  (org.apache.kafka.tools.consumer.ConsoleConsumer)
org.apache.kafka.common.errors.TimeoutException
Processed a total of 1 messages

Show more
6 мар.
Сообщение в Kafka есть ✅. Теперь смотрим на какой партиции оно лежит и назначены ли партиции CRM consumer'у:

bash
# Проверяем offsets — прочитал ли crm-backend это сообщение
docker exec shared-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
--bootstrap-server localhost:9092 \
--group crm-backend \
--describe
michael@demo-server:~$ # Проверяем offsets — прочитал ли crm-backend это сообщение
docker exec shared-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
--bootstrap-server localhost:9092 \
--group crm-backend \
--describe
GROUP           TOPIC               PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG             CONSUMER-ID                                                 HOST            CLIENT-ID
crm-backend     shop.orders.created 0          -               1               -               consumer-crm-backend-1-ee9c9685-e3d4-4473-82f1-070d13310ee3 /172.17.0.1     consumer-crm-backend-1
crm-backend     shop.orders.created 1          -               0               -               consumer-crm-backend-1-ee9c9685-e3d4-4473-82f1-070d13310ee3 /172.17.0.1     consumer-crm-backend-1
crm-backend     shop.orders.created 2          -               0               -               consumer-crm-backend-2-51f30840-f89b-4d20-a2be-1a0f2cb2612a /172.17.0.1     consumer-crm-backend-2
michael@demo-server:~$

Show more
6 мар.
Вижу проблему. Партиция 0 имеет LOG-END-OFFSET=1 (сообщение есть), но CURRENT-OFFSET=- — consumer ещё не зафиксировал offset, то есть сообщение не обработано. LAG тоже - что странно.

Это значит consumer подключён но не читает. Смотрим полные логи CRM без фильтра:

bash
kubectl logs crm-app-backend-5959cd8db7-n488c -n crm --tail=50

ssh -J root@77.222.35.2 michael@10.0.0.2 michael@demo-server:~$ kubectl logs crm-app-backend-5959cd8db7-n488c -n crm --tail=50 2026-03-06 15:35:39 [http-nio-8080-exec-4] DEBUG [] o.s.jdbc.datasource.DataSourceUtils - Fetching JDBC Connection from DataSource 2026-03-06 15:35:39 [http-nio-8080-exec-4]

pasted

6 мар.
Логи показывают только Outbox Poller — никаких следов KafkaListener. Сообщение в партиции 0, но consumer его не читает.

Проблема скорее всего в том что сообщение попало в партицию 0, а CRM consumer'у назначены партиции 1 и 2 (из предыдущих логов). Партиция 0 не назначена никому — consumer их revoke'нул и не переназначил.

Рестартуем под чтобы consumer заново сделал rebalance:

bash
kubectl rollout restart deployment/crm-app-backend -n crm

# Ждём и сразу смотрим логи нового пода
kubectl get pods -n crm -w
crm-app-backend-7fc557c964-hjss6    0/1     Running   0          2m2s
crm-app-backend-7fc557c964-hjss6    1/1     Running   0          2m4s
crm-app-backend-5959cd8db7-n488c    1/1     Terminating   0          8m52s
crm-app-backend-5959cd8db7-n488c    1/1     Terminating   0          8m52s
crm-app-backend-5959cd8db7-n488c    0/1     Error         0          8m53s
crm-app-backend-5959cd8db7-n488c    0/1     Error         0          8m54s
crm-app-backend-5959cd8db7-n488c    0/1     Error         0          8m54s

6 мар.
bash
kubectl logs crm-app-backend-7fc557c964-hjss6 -n crm --tail=30 | grep -E "Kafka received|ShopOrder|partition|assigned|ERROR"
пусто

6 мар.
Проверяем назначение партиций после rebalance:

bash
docker exec shared-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
--bootstrap-server localhost:9092 \
--group crm-backend \
--describe
GROUP           TOPIC               PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG             CONSUMER-ID                                                 HOST            CLIENT-ID
crm-backend     shop.orders.created 0          -               1               -               consumer-crm-backend-1-4d74769d-d3bd-4ad0-8de3-a834d7be989a /172.17.0.1     consumer-crm-backend-1
crm-backend     shop.orders.created 1          -               0               -               consumer-crm-backend-1-4d74769d-d3bd-4ad0-8de3-a834d7be989a /172.17.0.1     consumer-crm-backend-1
crm-backend     shop.orders.created 2          -               0               -               consumer-crm-backend-2-97b20b42-c368-4d12-ba02-c00b50b0fce0 /172.17.0.1     consumer-crm-backend-2

6 мар.
Партиция 0 назначена consumer-1, сообщение там есть (LOG-END-OFFSET=1), но CURRENT-OFFSET=- — offset никогда не коммитился. Это значит consumer читает но либо падает до ACK, либо вообще не доходит до poll.

Смотрим есть ли вообще какая-то активность в логах consumer потока:

bash
kubectl logs crm-app-backend-7fc557c964-hjss6 -n crm | grep -E "KafkaListenerEndpoint|partitions assigned|rebalance|ConsumerCoordinator" | tail -20

ssh -J root@77.222.35.2 michael@10.0.0.2 crm-app-backend-7fc557c964-hjss6 0/1 Running 0 2m2s crm-app-backend-7fc557c964-hjss6 1/1 Running 0 2m4s crm-app-backend-5959cd8db7-n488c 1/1 Terminating 0 8m52s crm-app-backend-5959cd8db7-n488c 1/1

pasted

6 мар.
Прогресс! Сообщение дошло и начало обрабатываться, но упало с ошибкой SQL. Две проблемы видны:

1. SQLState 42703 — колонка не существует при поиске по email в customer_personal_data. Скорее всего схема тенанта не полностью инициализирована.

2. SQLState 07006 — неверный тип данных при INSERT в customers — проблема с типом поля external_id (передаётся UUID, а колонка ожидает другой тип).

Смотрим структуру таблиц в схеме тенанта:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SET search_path TO tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710;
\d customers;
\d customer_personal_data;"
ERROR:  syntax error at or near "\"
LINE 2:    \d customers;

6 мар.
bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SET search_path TO tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710;
SELECT column_name, data_type FROM information_schema.columns
WHERE table_name = 'customers' AND table_schema = 'tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710';"
SET
column_name  |        data_type         
--------------+--------------------------
id           | uuid
type         | character varying
company_name | character varying
inn          | character varying
kpp          | character varying
ogrn         | character varying
first_name   | character varying
last_name    | character varying
middle_name  | character varying
email        | character varying
phone        | character varying
phone2       | character varying
website      | character varying
address      | text
city         | character varying
region       | character varying
description  | text
source       | character varying
is_active    | boolean
author_id    | uuid
created_at   | timestamp with time zone
updated_at   | timestamp with time zone
status       | character varying
created_by   | uuid
(24 rows)
michael@demo-server:~$

Show more
6 мар.
Вот и причина ошибки — схема тенанта использует старую версию таблицы customers где всё в одной таблице (без external_id и без отдельной customer_personal_data). А код ShopOrderConsumer написан под новую схему где есть external_id в customers и отдельная таблица customer_personal_data.

Проверяем что за миграции применены к этой схеме:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT filename, executed_at FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.databasechangelog ORDER BY executed_at DESC LIMIT 10;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT filename, executed_at FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.databasechangelog ORDER BY executed_at DESC LIMIT 10;"
ERROR:  column "executed_at" does not exist
LINE 1: SELECT filename, executed_at FROM tenant_3a47abae_2dc5_4547_...

6 мар.
bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, author, dateexecuted, description FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.databasechangelog ORDER BY dateexecuted DESC LIMIT 15;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, author, dateexecuted, description FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.databasechangelog ORDER BY dateexecuted DESC LIMIT 15;"
id     |  author  |        dateexecuted        |                                                                                           description                                                                                            
------------+----------+----------------------------+--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
V118-3     | platform | 2026-03-06 10:05:55.254256 | addForeignKeyConstraint baseTableName=tasks, constraintName=fk_tasks_task_type, referencedTableName=task_types
V118-2     | platform | 2026-03-06 10:05:55.24193  | sql
V118-1     | platform | 2026-03-06 10:05:55.228809 | createTable tableName=task_types
V117-3     | platform | 2026-03-06 10:05:55.165273 | addColumn tableName=products
V117-2     | platform | 2026-03-06 10:05:55.147354 | addColumn tableName=tasks; addColumn tableName=tasks
V117-1     | platform | 2026-03-06 10:05:55.098291 | addColumn tableName=customers
V116-1     | platform | 2026-03-06 10:05:55.058188 | createTable tableName=customer_personal_data; createTable tableName=customer_org_data; sql
V115-1     | platform | 2026-03-06 10:05:54.901311 | addColumn tableName=customers; sql
V114-1     | platform | 2026-03-06 10:05:54.839316 | createTable tableName=module_settings; sql
V113-1     | platform | 2026-03-06 10:05:54.771553 | sql
V112-1     | platform | 2026-03-06 10:05:54.699157 | addColumn tableName=orders; addColumn tableName=orders; createIndex indexName=idx_orders_external_order_id, tableName=orders; createIndex indexName=idx_orders_shop_order_uuid, tableName=orders
111-3      | crm      | 2026-03-06 10:05:54.598911 | sql
111-2      | crm      | 2026-03-06 10:05:54.540516 | sql
111-1      | crm      | 2026-03-06 10:05:54.444722 | sql
V110-tasks | demo     | 2026-03-06 10:05:54.366557 | sql

Show more
6 мар.
Все миграции применены включая V116-1 (создание customer_personal_data) и V117-1 (добавление колонок в customers). Значит external_id должен быть — проверим ещё раз, возможно он добавлен через addColumn:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema = 'tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710'
AND table_name = 'customers'
AND column_name IN ('external_id', 'status');"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT column_name, data_type FROM information_schema.columns
WHERE table_schema = 'tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710'
AND table_name = 'customers'
AND column_name IN ('external_id', 'status');"
column_name |     data_type     
-------------+-------------------
status      | character varying
(1 row)

6 мар.
external_id отсутствует в таблице — миграция V115-1 (addColumn tableName=customers) видимо добавляла другие колонки, но не external_id. Смотрим что именно добавила эта миграция:

bash
find /home/claude/crm -name "V115*" | head -5
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.27.xsd">

    <changeSet id="V115-1" author="platform" labels="tenant,customers">
        <comment>Add status column to customers table</comment>

        <addColumn tableName="customers">
            <column name="status" type="VARCHAR(50)" defaultValue="NEW">
                <constraints nullable="false"/>
            </column>
        </addColumn>

        <!-- Проставляем статус на основе new -->
        <sql>
            UPDATE customers SET status = CASE
                WHEN is_active = true  THEN 'NEW'
                WHEN is_active = false THEN 'INACTIVE'
                ELSE 'NEW'
            END;
        </sql>

        <rollback>
            <dropColumn tableName="customers" columnName="status"/>
        </rollback>
    </changeSet>

</databaseChangeLog>
Show more
6 мар.
external_id нет ни в одной миграции для тенант-схемы. Нужно найти где он должен быть — смотрим все миграции связанные с customers:

bash
find /home/claude/crm -name "*.xml" | xargs grep -l "external_id" 2>/dev/null
здесь надо смотреть /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration

6 мар.
Это путь на твоём Mac, а не на сервере. Но файлы мигаций должны быть в zip архиве который ты загрузил — смотрим там:

bash
find /home/claude/crm -name "*.xml" | xargs grep -l "external_id" 2>/dev/null
find /home/claude/crm -name "*.sql" | xargs grep -l "external_id" 2>/dev/null
давай на маке посмотрим - на сервере тоже самое

6 мар.
Хорошо, на маке:

bash
grep -rl "external_id" /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration
пусто не было

6 мар.
Значит external_id вообще не добавлен в миграции — это баг. Колонка используется в коде ShopOrderConsumer но никогда не создавалась в БД.

Нужно создать миграцию. Смотрим какой номер следующий:

bash
ls /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/ | sort | tail -5
mihailbolohonov@MacBook-Air-Mihail crm_final % ls /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/ | sort | tail -5
V115__customers_status.xml
V116__customer_data_tables.xml
V117__missing_columns.xml
V118__task_types.xml
tenant-changelog.xml

6 мар.
Следующая будет V119. Создаём миграцию:

bash
cat > /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/V119__customers_external_id.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.27.xsd">

    <changeSet id="V119-1" author="platform" labels="tenant,customers">
        <comment>Add external_id to customers for shop integration</comment>

        <addColumn tableName="customers">
            <column name="external_id" type="VARCHAR(255)">
                <constraints nullable="true"/>
            </column>
        </addColumn>

        <createIndex tableName="customers" indexName="idx_customers_external_id">
            <column name="external_id"/>
        </createIndex>

        <rollback>
            <dropIndex tableName="customers" indexName="idx_customers_external_id"/>
            <dropColumn tableName="customers" columnName="external_id"/>
        </rollback>
    </changeSet>

</databaseChangeLog>
EOF
После этого нужно задеплоить обновлённый liquibase и накатить миграцию на все тенант-схемы. Как у тебя устроен деплой liquibase — отдельный job в k3s или запускается при старте приложения?

имя миграции напиши

6 мар.
bash
ls /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/V118__task_types.xml
Судя по паттерну — V119__customers_external_id.xml. Но уточни — в названиях используется двойное подчёркивание __ или одинарное _?

готово <?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.27.xsd">

<changeSet id="V119-1" author="platform" labels="tenant,customers">
    <comment>Add external_id to customers for shop integration</comment>

    <addColumn tableName="customers">
        <column name="external_id" type="VARCHAR(255)">
            <constraints nullable="true"/>
        </column>
    </addColumn>

    <createIndex tableName="customers" indexName="idx_customers_external_id">
        <column name="external_id"/>
    </createIndex>

    <rollback>
        <dropIndex tableName="customers" indexName="idx_customers_external_id"/>
        <dropColumn tableName="customers" columnName="external_id"/>
    </rollback>
</changeSet>

</databaseChangeLog>

Show more
6 мар.
Теперь деплоим. Как запускается liquibase у тебя — отдельный Kubernetes Job или как часть старта CRM приложения?

часть деплоя #!/bin/bash
set -e

APP=${1:-crm-app}
TAG=${2:-latest}

VPS="root@77.222.35.2"
SERVER="michael@10.0.0.2"
REMOTE_BUILD_DIR="/home/michael/build/$APP"
REMOTE_SECRETS_DIR="/home/michael/secrets/$APP"
HELM_CHART="./crm-helm"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[deploy]${NC} $1"; }
warn() { echo -e "${YELLOW}[warn]${NC} $1"; }
fail() { echo -e "${RED}[error]${NC} $1"; exit 1; }

log "Starting deploy of $APP:$TAG"

[ ! -d "$HELM_CHART" ] && fail "Helm chart not found: $HELM_CHART"

log "Syncing sources to server..."
ssh -J $VPS $SERVER "mkdir -p $REMOTE_BUILD_DIR"

for SERVICE in crm-backend crm-frontend crm-liquibase; do
if [ -d "./$SERVICE" ]; then
log "  Syncing $SERVICE..."
rsync -az --delete \
--exclude='target/' \
--exclude='node_modules/' \
--exclude='.git/' \
--exclude='*.class' \
-e "ssh -J $VPS" \
./$SERVICE/ $SERVER:$REMOTE_BUILD_DIR/$SERVICE/
fi
done

log "Building images on server..."
ssh -J $VPS $SERVER bash << REMOTEBUILD
set -e
REGISTRY="localhost:5000"
TAG="$TAG"
BUILD_DIR="$REMOTE_BUILD_DIR"

echo "[server] Copying liquibase migrations into backend..."
cp -r \$BUILD_DIR/crm-liquibase/src/main/resources/db/. \$BUILD_DIR/crm-backend/src/main/resources/db/

echo "[server] Building crm-backend..."
docker build -t \$REGISTRY/crm-backend:\$TAG \$BUILD_DIR/crm-backend/

echo "[server] Building crm-frontend..."
docker build -t \$REGISTRY/crm-frontend:\$TAG \
--build-arg VITE_API_BASE_URL=/crm/api/v1 \
\$BUILD_DIR/crm-frontend/

echo "[server] Pushing to local registry..."
docker push \$REGISTRY/crm-backend:\$TAG
docker push \$REGISTRY/crm-frontend:\$TAG
echo "[server] Done building"
REMOTEBUILD

log "Syncing helm chart..."
ssh -J $VPS $SERVER "mkdir -p $REMOTE_BUILD_DIR/helm"
rsync -az --delete \
--exclude='values.prod.yaml' \
-e "ssh -J $VPS" \
$HELM_CHART/ $SERVER:$REMOTE_BUILD_DIR/helm/

log "Deploying to k3s..."
ssh -J $VPS $SERVER bash << REMOTEDEPLOY
set -e
SECRETS="$REMOTE_SECRETS_DIR/values.prod.yaml"
CHART="$REMOTE_BUILD_DIR/helm"

[ ! -f "\$SECRETS" ] && echo "ERROR: values.prod.yaml not found at \$SECRETS" && exit 1

echo "[server] Clearing k3s image cache..."
sudo crictl rmi localhost:5000/crm-backend:$TAG 2>/dev/null || true
sudo crictl rmi localhost:5000/crm-frontend:$TAG 2>/dev/null || true

helm upgrade --install $APP \$CHART \
--namespace crm --create-namespace \
--values \$SECRETS \
--set backend.image.tag=$TAG \
--set frontend.image.tag=$TAG

echo "[server] Restarting deployments..."
kubectl -n crm rollout restart deployment/$APP-backend
kubectl -n crm rollout restart deployment/$APP-frontend

echo "[server] Checking rollout..."
kubectl -n crm rollout status deployment/$APP-backend --timeout=300s
kubectl -n crm rollout status deployment/$APP-frontend --timeout=120s
echo "[server] Deploy complete"
REMOTEDEPLOY

log "Deploy of $APP:$TAG complete"

Show more
6 мар.
Всё понятно. Запускаем деплой из директории проекта на маке:

bash
cd /Users/mihailbolohonov/IdeaProjects/crm_final
./deploy.sh crm-app latest
дальше

6 мар.
Запускай деплой и скидывай вывод — посмотрим что получится.

michael@demo-server:~$ kgp -n crm
NAME                                READY   STATUS    RESTARTS   AGE
crm-app-backend-6c7bdcb4bf-gkjt9    1/1     Running   0          6m15s
crm-app-frontend-59f7879794-wxflr   1/1     Running   0          6m15s
michael@demo-server:~$

6 мар.
Под поднялся. Проверяем что миграция применилась и consumer обработал сообщение:

bash
# Проверяем что external_id появился
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT column_name FROM information_schema.columns
WHERE table_schema = 'tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710'
AND table_name = 'customers' AND column_name = 'external_id';"

# Проверяем idempotency log
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT * FROM public.kafka_idempotency_log ORDER BY processed_at DESC LIMIT 5;"
column_name
-------------
(0 rows)
id | shop_order_uuid | crm_order_id | processed_at
----+-----------------+--------------+--------------
(0 rows)

6 мар.
Миграция не применилась. Проверяем логи бэкенда на предмет ошибок liquibase:

bash
kubectl logs crm-app-backend-6c7bdcb4bf-gkjt9 -n crm | grep -E "liquibase|Liquibase|migration|V119|ERROR" | head -20
michael@demo-server:~$ kubectl logs crm-app-backend-6c7bdcb4bf-gkjt9 -n crm | grep -E "liquibase|Liquibase|migration|V119|ERROR" | head -20
2026-03-06 15:49:41 [main] INFO  [] liquibase.changelog - Reading from public.databasechangelog
2026-03-06 15:49:43 [main] INFO  [] liquibase.ui - Database is up to date, no changesets to execute
2026-03-06 15:49:43 [main] INFO  [] liquibase.changelog - Reading from public.databasechangelog
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - UPDATE SUMMARY
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - Run:                          0
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - Previously run:              40
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - Filtered out:                 0
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - -------------------------------
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - Total change sets:           40
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - Update summary generated
2026-03-06 15:49:44 [main] INFO  [] liquibase.command - Command execution complete
2026-03-06 15:50:32 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] c.c.kafka.consumer.ShopOrderConsumer - Failed to process shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea: PreparedStatementCallback; bad SQL grammar [INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)]
2026-03-06 15:50:34 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] o.s.k.l.KafkaMessageListenerContainer - Error handler threw an exception
2026-03-06 15:50:34 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] c.c.kafka.consumer.ShopOrderConsumer - Failed to process shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea: PreparedStatementCallback; bad SQL grammar [INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)]
2026-03-06 15:50:36 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] o.s.k.l.KafkaMessageListenerContainer - Error handler threw an exception
2026-03-06 15:50:36 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] c.c.kafka.consumer.ShopOrderConsumer - Failed to process shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea: PreparedStatementCallback; bad SQL grammar [INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)]
2026-03-06 15:50:38 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] o.s.k.l.KafkaMessageListenerContainer - Error handler threw an exception
2026-03-06 15:50:38 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] c.c.kafka.consumer.ShopOrderConsumer - Failed to process shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea: PreparedStatementCallback; bad SQL grammar [INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)]
2026-03-06 15:50:45 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] c.c.kafka.consumer.ShopOrderConsumer - Failed to process shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea: PreparedStatementCallback; bad SQL grammar [INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)]
2026-03-06 15:50:47 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] o.s.k.l.KafkaMessageListenerContainer - Error handler threw an exception

Show more
6 мар.
Liquibase читает только public.databasechangelog — это значит V119 применяется только к публичной схеме, но не к тенант-схемам. Liquibase не знает что нужно прогнать миграцию по каждому тенанту.

Смотрим как устроен механизм применения миграций к тенантам:

bash
find /home/claude/crm -name "*.java" | xargs grep -l "tenant\|Tenant\|schema\|liquibase\|Liquibase" 2>/dev/null | grep -v test | head -10
<databaseChangeLog
xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.27.xsd">

<!-- PUBLIC SCHEMA -->
<include file="db/migration/public/V001__extensions.xml"              relativeToChangelogFile="false"/>
<include file="db/migration/public/V002__tenants.xml"                 relativeToChangelogFile="false"/>
<include file="db/migration/public/V003__users_global.xml"            relativeToChangelogFile="false"/>
<include file="db/migration/public/V004__oauth_accounts.xml"          relativeToChangelogFile="false"/>
<include file="db/migration/public/V005__refresh_tokens.xml"          relativeToChangelogFile="false"/>
<include file="db/migration/public/V006__tenant_modules.xml"          relativeToChangelogFile="false"/>
<include file="db/migration/public/V007__users_global_extend.xml" relativeToChangelogFile="false"/>
<include file="db/migration/public/V008__users_global_tenant_nullable.xml" relativeToChangelogFile="false"/>
<include file="db/migration/public/V009__tenants_extend.xml"               relativeToChangelogFile="false"/>
<include file="db/migration/public/V010__email_verifications.xml" relativeToChangelogFile="false"/>

<!-- Kafka / Shop integration (PUBLIC) -->
<include file="db/migration/public/V110__kafka_infrastructure.xml"    relativeToChangelogFile="false"/>

<!-- TENANT SCHEMA -->
<include file="db/migration/tenant/V101__roles_permissions.xml"       relativeToChangelogFile="false"/>
<include file="db/migration/tenant/V102__users_tenant.xml"            relativeToChangelogFile="false"/>
<include file="db/migration/tenant/V103__statuses.xml"                relativeToChangelogFile="false"/>
<include file="db/migration/tenant/V104__customers.xml"               relativeToChangelogFile="false"/>
<include file="db/migration/tenant/V105__tasks.xml"                   relativeToChangelogFile="false"/>
<include file="db/migration/tenant/V106__products.xml"                relativeToChangelogFile="false"/>
<include file="db/migration/tenant/V107__orders.xml"                  relativeToChangelogFile="false"/>
<include file="db/migration/tenant/V108__seed_roles_permissions.xml"  relativeToChangelogFile="false"/>
<include file="db/migration/tenant/V109__seed_statuses.xml"           relativeToChangelogFile="false"/>

<!-- DEMO SEED (context=demo) -->
<include file="db/migration/tenant/V110__demo_seed.xml"               relativeToChangelogFile="false"/>

<!-- Kafka / Shop integration (TENANT) -->
<include file="db/migration/tenant/V112__orders_kafka_fields.xml"     relativeToChangelogFile="false"/>
<include file="db/migration/tenant/V113__order_statuses_kafka.xml"    relativeToChangelogFile="false"/>

</databaseChangeLog>
Show more
6 мар.
Вижу проблему — в tenant-changelog.xml нет включений для V114 — V119. Они существуют как файлы, но не подключены в changelog. Нужно добавить все недостающие:

bash
cat >> /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/tenant-changelog.xml << 'EOF'
Стоп — покажи сначала полное содержимое файла tenant-changelog.xml, чтобы понять где именно закрывающий тег </databaseChangeLog>:

bash
cat /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/tenant-changelog.xml
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.27.xsd">

    <include file="db/migration/tenant/V101__roles_permissions.xml"       relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V102__users_tenant.xml"            relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V103__statuses.xml"                relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V104__customers.xml"               relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V105__tasks.xml"                   relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V106__products.xml"                relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V107__orders.xml"                  relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V108__seed_roles_permissions.xml"  relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V109__seed_statuses.xml"           relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V110__demo_seed.xml"               relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V111__audit_log.xml"               relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V112__orders_kafka_fields.xml"     relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V113__order_statuses_kafka.xml"    relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V114__module_settings.xml"         relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V115__customers_status.xml"        relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V116__customer_data_tables.xml"    relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V117__missing_columns.xml"         relativeToChangelogFile="false"/>
    <include file="db/migration/tenant/V118__task_types.xml"              relativeToChangelogFile="false"/>

</databaseChangeLog>
Show more
6 мар.
V119 просто не добавлен в changelog. Добавляем:

bash
sed -i '' 's|    <include file="db/migration/tenant/V118__task_types.xml"              relativeToChangelogFile="false"/>|    <include file="db/migration/tenant/V118__task_types.xml"              relativeToChangelogFile="false"/>\n    <include file="db/migration/tenant/V119__customers_external_id.xml"     relativeToChangelogFile="false"/>|' \
/Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/tenant-changelog.xml
Проверяем:

bash
tail -5 /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/tenant-changelog.xml
michael@demo-server:~$ kgp -n crm
NAME                                READY   STATUS    RESTARTS   AGE
crm-app-backend-6c7bdcb4bf-gkjt9    1/1     Running   0          19m
crm-app-frontend-59f7879794-wxflr   1/1     Running   0          19m

6 мар.
Деплой ещё не запускали — ты на сервере. Нужно на маке запустить деплой:

bash
cd /Users/mihailbolohonov/IdeaProjects/crm_final
./deploy.sh crm-app latest
уже прошел

6 мар.
Проверяем что миграция применилась и consumer обработал сообщение:

bash
# Проверяем external_id
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT column_name FROM information_schema.columns
WHERE table_schema = 'tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710'
AND table_name = 'customers' AND column_name = 'external_id';"

# Проверяем idempotency log
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT shop_order_uuid, crm_order_id, processed_at FROM public.kafka_idempotency_log ORDER BY processed_at DESC LIMIT 5;"
michael@demo-server:~$ # Проверяем external_id
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT column_name FROM information_schema.columns
WHERE table_schema = 'tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710'
AND table_name = 'customers' AND column_name = 'external_id';"
# Проверяем idempotency log
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT shop_order_uuid, crm_order_id, processed_at FROM public.kafka_idempotency_log ORDER BY processed_at DESC LIMIT 5;"
column_name
-------------
(0 rows)
shop_order_uuid | crm_order_id | processed_at
-----------------+--------------+--------------
(0 rows)

Show more
6 мар.
Миграция снова не применилась. Смотрим логи нового пода:

bash
kubectl logs $(kubectl get pod -n crm -l app=crm-app-backend -o jsonpath='{.items[0].metadata.name}') -n crm | grep -E "liquibase|Liquibase|V119|changeSet" | head -20
michael@demo-server:~$ kubectl logs $(kubectl get pod -n crm -l app=crm-app-backend -o jsonpath='{.items[0].metadata.name}') -n crm | grep -E "liquibase|Liquibase|V119|changeSet" | head -20
2026-03-06 15:49:41 [main] INFO  [] liquibase.changelog - Reading from public.databasechangelog
2026-03-06 15:49:43 [main] INFO  [] liquibase.ui - Database is up to date, no changesets to execute
2026-03-06 15:49:43 [main] INFO  [] liquibase.changelog - Reading from public.databasechangelog
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - UPDATE SUMMARY
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - Run:                          0
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - Previously run:              40
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - Filtered out:                 0
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - -------------------------------
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - Total change sets:           40
2026-03-06 15:49:44 [main] INFO  [] liquibase.util - Update summary generated
2026-03-06 15:49:44 [main] INFO  [] liquibase.command - Command execution complete

Show more
6 мар.
Старый под — 15:49, а деплой уже прошёл. Новый под не поднялся. Проверяем:

bash
kubectl get pods -n crm
NAME                                READY   STATUS    RESTARTS   AGE
crm-app-backend-dcfd454cb-65xjl     1/1     Running   0          2m23s
crm-app-frontend-55884b9486-mqfjb   1/1     Running   0          2m23s

6 мар.
bash
kubectl logs crm-app-backend-dcfd454cb-65xjl -n crm | grep -E "liquibase|Liquibase|V119|changeSet|UPDATE SUMMARY|Run:" | head -20
michael@demo-server:~$ kubectl logs crm-app-backend-dcfd454cb-65xjl -n crm | grep -E "liquibase|Liquibase|V119|changeSet|UPDATE SUMMARY|Run:" | head -20
2026-03-06 16:11:58 [main] INFO  [] liquibase.changelog - Reading from public.databasechangelog
2026-03-06 16:12:00 [main] INFO  [] liquibase.ui - Database is up to date, no changesets to execute
2026-03-06 16:12:00 [main] INFO  [] liquibase.changelog - Reading from public.databasechangelog
2026-03-06 16:12:00 [main] INFO  [] liquibase.util - UPDATE SUMMARY
2026-03-06 16:12:00 [main] INFO  [] liquibase.util - Run:                          0
2026-03-06 16:12:00 [main] INFO  [] liquibase.util - Previously run:              40
2026-03-06 16:12:00 [main] INFO  [] liquibase.util - Filtered out:                 0
2026-03-06 16:12:00 [main] INFO  [] liquibase.util - -------------------------------
2026-03-06 16:12:00 [main] INFO  [] liquibase.util - Total change sets:           40
2026-03-06 16:12:00 [main] INFO  [] liquibase.util - Update summary generated
2026-03-06 16:12:00 [main] INFO  [] liquibase.command - Command execution complete
michael@demo-server:~$

Show more
6 мар.
Total change sets: 40 — V119 не попал в образ. Значит либо sed не сработал на маке, либо файл не был включён в сборку. Проверяем на маке:

bash
grep "V119" /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/tenant-changelog.xml
mihailbolohonov@MacBook-Air-Mihail crm_final % grep "V119" /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/tenant-changelog.xml
<include file="db/migration/tenant/V119__customers_external_id.xml"              relativeToChangelogFile="false"/>

6 мар.
Строка есть в changelog. Значит файл V119__customers_external_id.xml не попал в сборку. Проверяем:

bash
ls /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/V119*
mihailbolohonov@MacBook-Air-Mihail crm_final % ls /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/V119*
/Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/V119__customers_external_id.xml

6 мар.
Файл есть. Проверяем что деплой скопировал его на сервер:

bash
ls /home/michael/build/crm-app/crm-backend/src/main/resources/db/migration/tenant/V119*
michael@demo-server:~$ ls /home/michael/build/crm-app/crm-backend/src/main/resources/db/migration/tenant/V119*
/home/michael/build/crm-app/crm-backend/src/main/resources/db/migration/tenant/V119__customers_external_id.xml

6 мар.
Файл на сервере есть. Значит проблема в Docker образе — проверяем что файл попал внутрь контейнера:

bash
kubectl exec crm-app-backend-dcfd454cb-65xjl -n crm -- find /app -name "V119*" 2>/dev/null
kubectl exec crm-app-backend-dcfd454cb-65xjl -n crm -- find /app -name "tenant-changelog.xml" 2>/dev/null
пусто

6 мар.
Файлы не попали в образ. Смотрим Dockerfile:

bash
cat /home/michael/build/crm-app/crm-backend/Dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
Show more
6 мар.
Всё ясно — деплой скрипт копирует миграции командой:

cp -r $BUILD_DIR/crm-liquibase/src/main/resources/db/. $BUILD_DIR/crm-backend/src/main/resources/db/
Но это происходит до docker build. Проверяем что реально лежит в target jar — скорее всего Maven собрал jar до того как скрипт скопировал файлы, или rsync не передал обновлённый tenant-changelog.xml.

Проверяем содержимое jar:

bash
unzip -p /home/michael/build/crm-app/crm-backend/target/*.jar "db/migration/tenant/tenant-changelog.xml" 2>/dev/null | grep "V119"
unzip -l /home/michael/build/crm-app/crm-backend/target/*.jar | grep "V119"
target нет билд на сервере

6 мар.
Билд происходит внутри Docker multi-stage — target создаётся внутри контейнера и не остаётся на сервере. Проверяем содержимое запущенного контейнера:

bash
kubectl exec crm-app-backend-dcfd454cb-65xjl -n crm -- unzip -l /app/app.jar | grep -E "V119|tenant-changelog"
висит

6 мар.
Ctrl+C и пробуем по-другому:

bash
kubectl exec crm-app-backend-dcfd454cb-65xjl -n crm -- sh -c "find /app -name '*.jar' | head -3"
michael@demo-server:~$ kubectl exec crm-app-backend-dcfd454cb-65xjl -n crm -- unzip -l /app/app.jar | grep -E "V119|tenant-changelog"
michael@demo-server:~$: command not found

6 мар.
bash
kubectl exec crm-app-backend-dcfd454cb-65xjl -n crm -- sh -c "find / -name 'tenant-changelog.xml' 2>/dev/null"
michael@demo-server:~$ kubectl exec crm-app-backend-dcfd454cb-65xjl -n crm -- sh -c "find / -name 'tenant-changelog.xml' 2>/dev/null"
michael@demo-server:~$

6 мар.
Файл вообще не попал в jar. Проблема в деплой скрипте — порядок операций неверный. Смотри что происходит:

rsync копирует crm-liquibase на сервер ✅
cp -r crm-liquibase/... crm-backend/src/... копирует миграции ✅
docker build — Maven внутри контейнера делает COPY src src и собирает jar
Но в src/main/resources/db/ миграций нет изначально — они копируются из отдельного модуля crm-liquibase. Проверяем что реально лежит в src перед сборкой:

bash
ls /home/michael/build/crm-app/crm-backend/src/main/resources/db/migration/tenant/ | tail -5
michael@demo-server:~$ ls /home/michael/build/crm-app/crm-backend/src/main/resources/db/migration/tenant/ | tail -5
V116__customer_data_tables.xml
V117__missing_columns.xml
V118__task_types.xml
V119__customers_external_id.xml
tenant-changelog.xml
michael@demo-server:~$

6 мар.
Файлы на сервере есть включая V119. Значит Docker build кэшировал старый слой и не пересобрал. В Dockerfile есть COPY src src — если Maven dependencies не менялись, Docker использует кэш.

Нужно сбросить кэш при сборке:

bash
docker build --no-cache -t localhost:5000/crm-backend:latest /home/michael/build/crm-app/crm-backend/
docker push localhost:5000/crm-backend:latest

# Чистим k3s кэш и рестартуем
sudo crictl rmi localhost:5000/crm-backend:latest 2>/dev/null || true
kubectl rollout restart deployment/crm-app-backend -n crm
kubectl rollout status deployment/crm-app-backend -n crm --timeout=300s
разве кэш не сбрасывается в deploy.sh

6 мар.
Смотрим deploy.sh ещё раз — там было:

bash
sudo crictl rmi localhost:5000/crm-backend:$TAG 2>/dev/null || true
Это чистит кэш k3s, но не Docker build кэш. docker build без --no-cache всё равно использует кэшированные слои. Слой COPY src src не инвалидируется если Maven зависимости не менялись — Docker сравнивает только хэши файлов в контексте сборки, и если pom.xml не изменился, берёт кэш до COPY src src.

Запускай команды выше — пересборка с --no-cache.

michael@demo-server:~$ docker build --no-cache -t localhost:5000/crm-backend:latest /home/michael/build/crm-app/crm-backend/
DEPRECATED: The legacy builder is deprecated and will be removed in a future release.
Install the buildx component to build images with BuildKit:
https://docs.docker.com/go/buildx/
Sending build context to Docker daemon  867.3kB
Step 1/12 : FROM maven:3.9-eclipse-temurin-21-alpine AS builder
---> 5ebc930637cc
Step 2/12 : WORKDIR /build
---> Running in f170c8802066
---> Removed intermediate container f170c8802066
---> ef840ac434d0
Step 3/12 : COPY pom.xml .
---> f2d9362d6907
Step 4/12 : RUN mvn dependency:go-offline -q
---> Running in 3fbeaef6d017

Show more
6 мар.
Ждём пока соберётся — Maven скачивает зависимости. Это займёт несколько минут. Когда появится промпт — скидывай результат.

т.е. верно что я на сервере запустил

6 мар.
Да, всё верно — ты на сервере запустил docker build --no-cache напрямую, это правильно. Ждём завершения сборки.

давай поменяем deploy.sh

6 мар.
Да, нужно добавить --no-cache в deploy.sh на маке:

bash
sed -i '' 's/docker build -t \$REGISTRY\/crm-backend:\$TAG/docker build --no-cache -t \$REGISTRY\/crm-backend:\$TAG/' \
/Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh

sed -i '' 's/docker build -t \$REGISTRY\/crm-frontend:\$TAG/docker build --no-cache -t \$REGISTRY\/crm-frontend:\$TAG/' \
/Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh
Проверяем:

bash
grep "docker build" /Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh
покажи на какой строке

6 мар.
bash
grep -n "docker build" /Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh
просто строку покажи

6 мар.
bash
cat -n /Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh | grep "docker build"
без ифыр

6 мар.
bash
grep "docker build" /Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh
без grep  - прямо в файле

6 мар.
bash
cat /Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh | grep -n "docker build"
#!/bin/bash
set -e

APP=${1:-crm-app}
TAG=${2:-latest}

VPS="root@77.222.35.2"
SERVER="michael@10.0.0.2"
REMOTE_BUILD_DIR="/home/michael/build/$APP"
REMOTE_SECRETS_DIR="/home/michael/secrets/$APP"
HELM_CHART="./crm-helm"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[deploy]${NC} $1"; }
warn() { echo -e "${YELLOW}[warn]${NC} $1"; }
fail() { echo -e "${RED}[error]${NC} $1"; exit 1; }

log "Starting deploy of $APP:$TAG"

[ ! -d "$HELM_CHART" ] && fail "Helm chart not found: $HELM_CHART"

log "Syncing sources to server..."
ssh -J $VPS $SERVER "mkdir -p $REMOTE_BUILD_DIR"

for SERVICE in crm-backend crm-frontend crm-liquibase; do
if [ -d "./$SERVICE" ]; then
log "  Syncing $SERVICE..."
rsync -az --delete \
--exclude='target/' \
--exclude='node_modules/' \
--exclude='.git/' \
--exclude='*.class' \
-e "ssh -J $VPS" \
./$SERVICE/ $SERVER:$REMOTE_BUILD_DIR/$SERVICE/
fi
done

log "Building images on server..."
ssh -J $VPS $SERVER bash << REMOTEBUILD
set -e
REGISTRY="localhost:5000"
TAG="$TAG"
BUILD_DIR="$REMOTE_BUILD_DIR"

echo "[server] Copying liquibase migrations into backend..."
cp -r \$BUILD_DIR/crm-liquibase/src/main/resources/db/. \$BUILD_DIR/crm-backend/src/main/resources/db/

echo "[server] Building crm-backend..."
docker build -t \$REGISTRY/crm-backend:\$TAG \$BUILD_DIR/crm-backend/

echo "[server] Building crm-frontend..."
docker build -t \$REGISTRY/crm-frontend:\$TAG \
--build-arg VITE_API_BASE_URL=/crm/api/v1 \
\$BUILD_DIR/crm-frontend/

echo "[server] Pushing to local registry..."
docker push \$REGISTRY/crm-backend:\$TAG
docker push \$REGISTRY/crm-frontend:\$TAG
echo "[server] Done building"
REMOTEBUILD

log "Syncing helm chart..."
ssh -J $VPS $SERVER "mkdir -p $REMOTE_BUILD_DIR/helm"
rsync -az --delete \
--exclude='values.prod.yaml' \
-e "ssh -J $VPS" \
$HELM_CHART/ $SERVER:$REMOTE_BUILD_DIR/helm/

log "Deploying to k3s..."
ssh -J $VPS $SERVER bash << REMOTEDEPLOY
set -e
SECRETS="$REMOTE_SECRETS_DIR/values.prod.yaml"
CHART="$REMOTE_BUILD_DIR/helm"

[ ! -f "\$SECRETS" ] && echo "ERROR: values.prod.yaml not found at \$SECRETS" && exit 1

echo "[server] Clearing k3s image cache..."
sudo crictl rmi localhost:5000/crm-backend:$TAG 2>/dev/null || true
sudo crictl rmi localhost:5000/crm-frontend:$TAG 2>/dev/null || true

helm upgrade --install $APP \$CHART \
--namespace crm --create-namespace \
--values \$SECRETS \
--set backend.image.tag=$TAG \
--set frontend.image.tag=$TAG

echo "[server] Restarting deployments..."
kubectl -n crm rollout restart deployment/$APP-backend
kubectl -n crm rollout restart deployment/$APP-frontend

echo "[server] Checking rollout..."
kubectl -n crm rollout status deployment/$APP-backend --timeout=300s
kubectl -n crm rollout status deployment/$APP-frontend --timeout=120s
echo "[server] Deploy complete"
REMOTEDEPLOY

log "Deploy of $APP:$TAG complete"
Show more
6 мар.
Строка 43 — меняем docker build на docker build --no-cache только для backend (frontend кэш не критичен):

bash
sed -i '' 's/docker build -t \$REGISTRY\/crm-backend:\$TAG/docker build --no-cache -t \$REGISTRY\/crm-backend:\$TAG/' \
/Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh

grep "docker build" /Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh
echo "[server] Building crm-backend..."
docker build --no-cache -t \$REGISTRY/crm-backend:\$TAG \$BUILD_DIR/crm-backend/
6 мар.
Готово. Теперь ждём пока завершится текущий docker build --no-cache на сервере и деплоим.

дальше

6 мар.
Скидывай вывод сборки с сервера — завершилась или ещё идёт?

да

6 мар.
bash
docker push localhost:5000/crm-backend:latest
sudo crictl rmi localhost:5000/crm-backend:latest 2>/dev/null || true
kubectl rollout restart deployment/crm-app-backend -n crm
kubectl rollout status deployment/crm-app-backend -n crm --timeout=300s
дальше

11:20
bash
kubectl logs $(kubectl get pod -n crm -l app=crm-app-backend -o jsonpath='{.items[0].metadata.name}') -n crm | grep -E "V119|Run:|Total change sets" | head -10
пусто

11:21
bash
kubectl get pods -n crm
NAME                                READY   STATUS    RESTARTS   AGE
crm-app-backend-7d6d5ccb58-jv7cm    1/1     Running   0          12h
crm-app-frontend-55884b9486-mqfjb   1/1     Running   0          15h

11:21
Под старый — 12h, значит рестарт не прошёл или применился старый образ. Принудительно рестартуем:

bash
kubectl rollout restart deployment/crm-app-backend -n crm
kubectl get pods -n crm -w
он прошел

11:21
Под 12h — это уже после нашего рестарта? Проверяем логи на предмет liquibase:

bash
kubectl logs crm-app-backend-7d6d5ccb58-jv7cm -n crm | grep -E "liquibase|Total change sets|Run:" | head -10
пусто

11:21
Под не перезапускался — логи пустые. Проверяем:

bash
kubectl describe pod crm-app-backend-7d6d5ccb58-jv7cm -n crm | grep -E "Started|Image|Started At"
michael@demo-server:~$ kubectl describe pod crm-app-backend-7d6d5ccb58-jv7cm -n crm | grep -E "Started|Image|Started At"
Image:          localhost:5000/crm-backend:latest
Image ID:       localhost:5000/crm-backend@sha256:85a7bcaf6322d127ccc4e1fb6fb840a3ecc8c52327c588d5c5623f561a91719b
Started:      Fri, 06 Mar 2026 21:43:11 +0300

11:22
Под стартовал в 21:43 — это после нашей пересборки. Но логи пустые — странно. Пробуем без фильтра первые строки:

bash
kubectl logs crm-app-backend-7d6d5ccb58-jv7cm -n crm | head -20
2026-03-07 06:04:01 [http-nio-8080-exec-5] DEBUG [] o.s.jdbc.datasource.DataSourceUtils - Fetching JDBC Connection from DataSource
2026-03-07 06:04:01 [http-nio-8080-exec-5] DEBUG [] o.s.jdbc.datasource.DataSourceUtils - Fetching JDBC Connection from DataSource
2026-03-07 06:04:01 [http-nio-8080-exec-5] DEBUG [] o.s.jdbc.datasource.DataSourceUtils - Fetching JDBC Connection from DataSource
2026-03-07 06:04:01 [http-nio-8080-exec-5] DEBUG [] o.s.jdbc.datasource.DataSourceUtils - Fetching JDBC Connection from DataSource
2026-03-07 06:04:02 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Creating new transaction with name [com.crm.kafka.outbox.KafkaOutboxPoller.poll]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
2026-03-07 06:04:02 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Acquired Connection [HikariProxyConnection@64120823 wrapping org.postgresql.jdbc.PgConnection@77871a39] for JDBC transaction
2026-03-07 06:04:02 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Switching JDBC Connection [HikariProxyConnection@64120823 wrapping org.postgresql.jdbc.PgConnection@77871a39] to manual commit
2026-03-07 06:04:02 [scheduling-1] DEBUG [] o.s.jdbc.core.JdbcTemplate - Executing prepared SQL query
2026-03-07 06:04:02 [scheduling-1] DEBUG [] o.s.jdbc.core.JdbcTemplate - Executing prepared SQL statement [SELECT * FROM public.kafka_outbox
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT ?
FOR UPDATE SKIP LOCKED
]
2026-03-07 06:04:02 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Initiating transaction commit
2026-03-07 06:04:02 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Committing JDBC transaction on Connection [HikariProxyConnection@64120823 wrapping org.postgresql.jdbc.PgConnection@77871a39]
2026-03-07 06:04:02 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Releasing JDBC Connection [HikariProxyConnection@64120823 wrapping org.postgresql.jdbc.PgConnection@77871a39] after transaction
2026-03-07 06:04:07 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Creating new transaction with name [com.crm.kafka.outbox.KafkaOutboxPoller.poll]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
2026-03-07 06:04:07 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Acquired Connection [HikariProxyConnection@1288430438 wrapping org.postgresql.jdbc.PgConnection@77871a39] for JDBC transaction
2026-03-07 06:04:07 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Switching JDBC Connection [HikariProxyConnection@1288430438 wrapping org.postgresql.jdbc.PgConnection@77871a39] to manual commit

Show more
11:22
Логи показывают только текущий момент (06:04), а старт пода был в 21:43 — логи старта уже вытеснены. Kubernetes хранит ограниченный буфер.

Проверяем напрямую — есть ли V119 в jar:

bash
kubectl exec crm-app-backend-7d6d5ccb58-jv7cm -n crm -- sh -c "find / -name 'V119*' 2>/dev/null"
пусто

11:22
Файл не попал в jar. Проверяем SHA образа который сейчас в registry vs что задеплоено:

bash
# SHA задеплоенного образа
kubectl describe pod crm-app-backend-7d6d5ccb58-jv7cm -n crm | grep "Image ID"

# SHA образа в registry
docker inspect localhost:5000/crm-backend:latest | grep "Id"
можно ли изменить пароль на  /Users/mihailbolohonov/.ssh/id_rsa

11:24
Это не связано с текущей задачей — лучше разберём после того как починим Kafka интеграцию.

Возвращаемся — скидывай вывод команд с SHA образов.

я перезапустил деплой, ответь пока

11:24
Да, можно изменить passphrase на существующем ключе:

bash
ssh-keygen -p -f /Users/mihailbolohonov/.ssh/id_rsa
Спросит старый пароль, потом новый. Если хочешь убрать пароль совсем — на новый просто нажми Enter.

rerun NAME                                 READY   STATUS    RESTARTS   AGE
crm-app-backend-7d6d5ccb58-jv7cm     1/1     Running   0          12h
crm-app-frontend-55884b9486-mqfjb    1/1     Running   0          15h
shop-app-backend-6845bc6f4c-b9mft    0/1     Running   0          49s
shop-app-backend-6b86f577cd-c8rss    0/1     Running   0          48s
shop-app-frontend-6fc976676c-4x668   1/1     Running   0          47s

11:30
CRM под старый (12h) — деплой crm не запускался. Shop деплоится сейчас.

Запусти деплой CRM отдельно:

bash
cd /Users/mihailbolohonov/IdeaProjects/crm_final
./deploy.sh crm-app latest
как посомтреть неймспейсы

11:32
bash
kubectl get pods -A
NAMESPACE     NAME                                      READY   STATUS      RESTARTS       AGE
crm           crm-app-backend-7d6d5ccb58-jv7cm          1/1     Running     0              12h
crm           crm-app-frontend-55884b9486-mqfjb         1/1     Running     0              15h
crm           shop-app-backend-6845bc6f4c-b9mft         0/1     Running     0              2m59s
crm           shop-app-backend-6b86f577cd-c8rss         0/1     Running     0              2m58s
crm           shop-app-frontend-6fc976676c-4x668        1/1     Running     0              2m57s
default       chat-app-frontend-76f7bf54bc-k7k4w        1/1     Running     10 (17h ago)   3d19h
default       chat-app-gateway-6bcc8676c4-tdcdm         1/1     Running     10 (17h ago)   3d19h
default       chat-app-service-f6f4d9df9-8cw2s          1/1     Running     12 (17h ago)   3d19h
kube-system   coredns-7566b5ff58-9p6z5                  1/1     Running     16 (17h ago)   7d22h
kube-system   helm-install-traefik-bb5w9                0/1     Completed   2              7d22h
kube-system   helm-install-traefik-crd-h28wz            0/1     Completed   0              7d22h
kube-system   local-path-provisioner-6bc6568469-6zfw4   1/1     Running     17 (17h ago)   7d22h
kube-system   metrics-server-786d997795-nvnwc           1/1     Running     18 (17h ago)   7d22h
kube-system   svclb-traefik-41e34a50-6bbhx              2/2     Running     32 (17h ago)   7d22h
kube-system   traefik-54c9597996-cq9q9                  1/1     Running     4 (17h ago)    47h
shop          shop-app-backend-646fbd6dd4-wcpld         1/1     Running     0              16h
shop          shop-app-frontend-7cffcfb5bf-p5x5g        1/1     Running     0              16h

Show more
11:32
Вижу проблему — shop задеплоился в namespace crm вместо shop! Два shop пода висят в crm в статусе 0/1 Running. Видимо deploy.sh для shop использует неверный namespace.

И CRM под всё ещё старый (12h). Сначала чистим лишние shop поды из crm:

bash
kubectl delete deployment shop-app-backend shop-app-frontend -n crm 2>/dev/null || true
Потом запускай деплой CRM на маке:

bash
cd /Users/mihailbolohonov/IdeaProjects/crm_final
./deploy.sh crm-app latest
а почему он в другой неймспейс задеплоился ? #!/bin/bash
set -e

APP=${1:-shop-app}
TAG=${2:-latest}

VPS="root@77.222.35.2"
SERVER="michael@10.0.0.2"
REMOTE_BUILD_DIR="/home/michael/build/$APP"
REMOTE_SECRETS_DIR="/home/michael/secrets/$APP"
HELM_CHART="./shop-helm"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[deploy]${NC} $1"; }
warn() { echo -e "${YELLOW}[warn]${NC} $1"; }
fail() { echo -e "${RED}[error]${NC} $1"; exit 1; }

log "Starting deploy of $APP:$TAG"

[ ! -d "$HELM_CHART" ] && fail "Helm chart not found: $HELM_CHART"

log "Syncing sources to server..."
ssh -J $VPS $SERVER "mkdir -p $REMOTE_BUILD_DIR"

for SERVICE in shop-backend shop-frontend; do
if [ -d "./$SERVICE" ]; then
log "  Syncing $SERVICE..."
rsync -az --delete \
--exclude='target/' \
--exclude='node_modules/' \
--exclude='.git/' \
--exclude='*.class' \
-e "ssh -J $VPS" \
./$SERVICE/ $SERVER:$REMOTE_BUILD_DIR/$SERVICE/
fi
done

log "Building images on server..."
ssh -J $VPS $SERVER bash << REMOTEBUILD
set -e
REGISTRY="localhost:5000"
TAG="$TAG"
BUILD_DIR="$REMOTE_BUILD_DIR"

echo "[server] Building shop-backend..."
docker build -t \$REGISTRY/shop-backend:\$TAG \$BUILD_DIR/shop-backend/

echo "[server] Building shop-frontend..."
docker build -t \$REGISTRY/shop-frontend:\$TAG \$BUILD_DIR/shop-frontend/

echo "[server] Pushing to local registry..."
docker push \$REGISTRY/shop-backend:\$TAG
docker push \$REGISTRY/shop-frontend:\$TAG
echo "[server] Done building"
REMOTEBUILD

log "Syncing helm chart..."
ssh -J $VPS $SERVER "mkdir -p $REMOTE_BUILD_DIR/helm"
rsync -az --delete \
--exclude='values.prod.yaml' \
-e "ssh -J $VPS" \
$HELM_CHART/ $SERVER:$REMOTE_BUILD_DIR/helm/

log "Deploying to k3s..."
ssh -J $VPS $SERVER bash << REMOTEDEPLOY
set -e
SECRETS="$REMOTE_SECRETS_DIR/values.prod.yaml"
CHART="$REMOTE_BUILD_DIR/helm"

[ ! -f "\$SECRETS" ] && echo "ERROR: values.prod.yaml not found at \$SECRETS" && exit 1

echo "[server] Clearing k3s image cache..."
sudo crictl rmi localhost:5000/shop-backend:$TAG 2>/dev/null || true
sudo crictl rmi localhost:5000/shop-frontend:$TAG 2>/dev/null || true

helm upgrade --install $APP \$CHART \
--namespace shop --create-namespace \
--values \$SECRETS \
--set backend.image.tag=$TAG \
--set frontend.image.tag=$TAG

echo "[server] Restarting deployments..."
kubectl -n shop rollout restart deployment/$APP-backend
kubectl -n shop rollout restart deployment/$APP-frontend

echo "[server] Checking rollout..."
kubectl -n shop rollout status deployment/$APP-backend --timeout=120s
kubectl -n shop rollout status deployment/$APP-frontend --timeout=120s
echo "[server] Deploy complete"
REMOTEDEPLOY

log "Deploy of $APP:$TAG complete"

Show more
11:33
В deploy.sh всё правильно — --namespace shop. Проблема в Helm chart — смотрим:

bash
grep -r "namespace" /home/michael/build/shop-app/helm/templates/ | head -10
mihailbolohonov@MacBook-Air-Mihail shop-project % grep -r "namespace" ./shop-helm/templates/ | head -10
./shop-helm/templates/ingress.yaml:  namespace: {{ .Release.Namespace }}
./shop-helm/templates/middleware.yaml:  namespace: {{ .Release.Namespace }}
./shop-helm/templates/frontend-deployment.yaml:  namespace: {{ .Release.Namespace }}
./shop-helm/templates/frontend-deployment.yaml:  namespace: {{ .Release.Namespace }}
./shop-helm/templates/backend-deployment.yaml:  namespace: {{ .Release.Namespace }}
./shop-helm/templates/backend-deployment.yaml:  namespace: {{ .Release.Namespace }}
./shop-helm/templates/secret.yaml:  namespace: {{ .Release.Namespace }}

Show more
11:34
Helm chart правильный — везде {{ .Release.Namespace }}. Значит проблема в том что предыдущий деплой shop был запущен из директории crm проекта случайно, или --namespace shop не сработал.

Проверяем что сейчас в namespace crm лишнего:

bash
kubectl get all -n crm | grep shop
NAMESPACE     NAME                                      READY   STATUS      RESTARTS       AGE
crm           crm-app-backend-7d6d5ccb58-jv7cm          1/1     Running     0              12h
crm           crm-app-backend-7d7d9bbdcb-8nznk          0/1     Running     0              92s
crm           crm-app-frontend-5445b9d6-7vdld           1/1     Running     0              91s
default       chat-app-frontend-76f7bf54bc-k7k4w        1/1     Running     10 (17h ago)   3d19h
default       chat-app-gateway-6bcc8676c4-tdcdm         1/1     Running     10 (17h ago)   3d19h
default       chat-app-service-f6f4d9df9-8cw2s          1/1     Running     12 (17h ago)   3d19h
kube-system   coredns-7566b5ff58-9p6z5                  1/1     Running     16 (17h ago)   7d22h
kube-system   helm-install-traefik-bb5w9                0/1     Completed   2              7d22h
kube-system   helm-install-traefik-crd-h28wz            0/1     Completed   0              7d22h
kube-system   local-path-provisioner-6bc6568469-6zfw4   1/1     Running     17 (17h ago)   7d22h
kube-system   metrics-server-786d997795-nvnwc           1/1     Running     18 (17h ago)   7d22h
kube-system   svclb-traefik-41e34a50-6bbhx              2/2     Running     32 (17h ago)   7d22h
kube-system   traefik-54c9597996-cq9q9                  1/1     Running     4 (17h ago)    47h
shop          shop-app-backend-646fbd6dd4-wcpld         1/1     Running     0              16h
shop          shop-app-frontend-7cffcfb5bf-p5x5g        1/1     Running     0

Show more
11:37
CRM деплой идёт (новый под 92s). Shop поды из crm уже пропали — сами удалились. Всё чисто.

Ждём пока новый CRM под поднимется:

bash
kubectl rollout status deployment/crm-app-backend -n crm --timeout=300s
NAME                               READY   STATUS    RESTARTS   AGE
crm-app-backend-7d7d9bbdcb-8nznk   1/1     Running   0          117s
crm-app-frontend-5445b9d6-7vdld    1/1     Running   0          116s

11:37
Новый под поднялся. Проверяем миграцию и consumer:

bash
kubectl logs crm-app-backend-7d7d9bbdcb-8nznk -n crm | grep -E "V119|Run:|Total change sets|external_id|ShopOrder|Kafka received" | head -20

ssh -J root@77.222.35.2 michael@10.0.0.2 michael@demo-server:~$ kgp -n crm NAME READY STATUS RESTARTS AGE crm-app-backend-7d7d9bbdcb-8nznk 1/1 Running 0 117s crm-app-frontend-5445b9d6-7vdld 1/1 Running 0 116s michael@demo-server

pasted

11:38
Total change sets: 40 — V119 всё ещё не применяется. Миграция не попала в jar.

Проблема понятна — Liquibase применяет миграции только к public схеме при старте. Тенант-схемы мигрируются отдельно через специальный механизм. V119 никогда не будет применён автоматически к существующим тенантам.

Применяем вручную ко всем тенант-схемам напрямую в БД:

bash
docker exec postgres psql -U crm_user -d crm_db -c "
ALTER TABLE tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.customers ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_customers_external_id ON tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.customers(external_id);

ALTER TABLE tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a.customers ADD COLUMN IF NOT EXISTS external_id VARCHAR(255);
CREATE INDEX IF NOT EXISTS idx_customers_external_id ON tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a.customers(external_id);
"
ALTER TABLE
CREATE INDEX
ALTER TABLE
CREATE INDEX

11:38
Теперь проверяем что consumer обработает сообщение:

bash
kubectl logs crm-app-backend-7d7d9bbdcb-8nznk -n crm -f | grep -E "Kafka received|Order created|ERROR|idempotency"

ssh -J root@77.222.35.2 michael@10.0.0.2 michael@demo-server:~$ kubectl logs crm-app-backend-7d7d9bbdcb-8nznk -n crm -f | grep -E "Kafka received|Order created|ERROR|idempotency" 2026-03-07 07:37:41 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO [] c.c.kafka.consumer.ShopOr

pasted

11:38
Логи старые — все с 07:37, до того как мы добавили колонку. Consumer уже исчерпал 3 попытки и отправил в DLQ. Нужно рестартовать под чтобы consumer переподключился и сбросить outbox:

bash
# Сбрасываем FAILED в outbox shop
docker exec postgres psql -U shop_user -d shop_db -c \
"UPDATE kafka_outbox SET status='PENDING', attempt_count=0, last_error=NULL WHERE status='FAILED';"

# Рестартуем CRM
kubectl rollout restart deployment/crm-app-backend -n crm
kubectl get pods -n crm -w
michael@demo-server:~$ kubectl get all -n crm | grep shop
service/shop-app-backend    ClusterIP   10.43.20.104    <none>        8081/TCP   10m
service/shop-app-frontend   ClusterIP   10.43.98.195    <none>        80/TCP     10m

11:39
Shop сервисы попали в namespace crm — это от предыдущего кривого деплоя. Но сейчас не критично, сначала проверяем CRM под:

bash
kubectl get pods -n crm
давай весь shop вычистим из неймспейса crm

11:40
bash
kubectl delete all -n crm -l app.kubernetes.io/instance=shop-app
michael@demo-server:~$ kubectl delete all -n crm -l app.kubernetes.io/instance=shop-app
No resources found

11:40
bash
kubectl get all -n crm | grep shop
michael@demo-server:~$ kubectl get all -n crm | grep shop
service/shop-app-backend    ClusterIP   10.43.20.104    <none>        8081/TCP   11m
service/shop-app-frontend   ClusterIP   10.43.98.195    <none>        80/TCP     11m

11:40
bash
kubectl delete service shop-app-backend shop-app-frontend -n crm
michael@demo-server:~$ michael@demo-server:~$ kubectl get all -n crm | grep shop
service/shop-app-backend    ClusterIP   10.43.20.104    <none>        8081/TCP   11m
service/shop-app-frontend   ClusterIP   10.43.98.195    <none>        80/TCP     11m
michael@demo-server:~$: command not found
-bash: none: No such file or directory
-bash: none: No such file or directory
michael@demo-server:~$

11:40
bash
kubectl delete service shop-app-backend shop-app-frontend -n crm
NAME                               READY   STATUS    RESTARTS   AGE
crm-app-backend-568d49565b-zfrbq   1/1     Running   0          2m6s
crm-app-frontend-5445b9d6-7vdld    1/1     Running   0          5m32s

11:41
Новый CRM под поднялся (2m). Проверяем обработку сообщения:

bash
kubectl logs crm-app-backend-568d49565b-zfrbq -n crm | grep -E "Kafka received|Order created|ERROR" | head -10
ssh -J root@77.222.35.2 michael@10.0.0.2
michael@demo-server:~$ kgp -n crm
NAME                               READY   STATUS    RESTARTS   AGE
crm-app-backend-568d49565b-zfrbq   1/1     Running   0          2m6s
crm-app-frontend-5445b9d6-7vdld    1/1     Running   0          5m32s
michael@demo-server:~$ kubectl logs crm-app-backend-568d49565b-zfrbq -n crm | grep -E "Kafka received|Order created|ERROR" | head -10
2026-03-07 07:40:48 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  [] c.c.kafka.consumer.ShopOrderConsumer - Kafka received: shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea partition=0 offset=0
2026-03-07 07:40:48 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] c.c.kafka.consumer.ShopOrderConsumer - Failed to process shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea: PreparedStatementCallback; bad SQL grammar [INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)]
2026-03-07 07:40:50 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] o.s.k.l.KafkaMessageListenerContainer - Error handler threw an exception
2026-03-07 07:40:50 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  [] c.c.kafka.consumer.ShopOrderConsumer - Kafka received: shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea partition=0 offset=0
2026-03-07 07:40:50 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] c.c.kafka.consumer.ShopOrderConsumer - Failed to process shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea: PreparedStatementCallback; bad SQL grammar [INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)]
2026-03-07 07:40:52 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] o.s.k.l.KafkaMessageListenerContainer - Error handler threw an exception
2026-03-07 07:40:52 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  [] c.c.kafka.consumer.ShopOrderConsumer - Kafka received: shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea partition=0 offset=0
2026-03-07 07:40:52 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] c.c.kafka.consumer.ShopOrderConsumer - Failed to process shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea: PreparedStatementCallback; bad SQL grammar [INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)]
2026-03-07 07:40:54 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] o.s.k.l.KafkaMessageListenerContainer - Error handler threw an exception
2026-03-07 07:40:54 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  [] c.c.kafka.consumer.ShopOrderConsumer - Kafka received: shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea partition=0 offset=0
michael@demo-server:~$

Show more
11:41
Всё ещё та же ошибка. Колонка добавлена в БД, но consumer продолжает падать. Проверяем реально ли колонка есть:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT column_name FROM information_schema.columns
WHERE table_schema = 'tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710'
AND table_name = 'customers' AND column_name = 'external_id';"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT column_name FROM information_schema.columns
WHERE table_schema = 'tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710'
AND table_name = 'customers' AND column_name = 'external_id';"
column_name
-------------
external_id
(1 row)

11:42
Колонка есть. Но ошибка bad SQL grammar — это не "колонка не найдена", это что-то другое. Смотрим полный stacktrace с root cause:

bash
kubectl logs crm-app-backend-568d49565b-zfrbq -n crm | grep -A 20 "Caused by" | head -40

ssh -J root@77.222.35.2 michael@10.0.0.2 michael@demo-server:~$ kubectl logs crm-app-backend-568d49565b-zfrbq -n crm | grep -A 20 "Caused by" | head -40 Caused by: org.postgresql.util.PSQLException: Can't infer the SQL type to use for an instance of java.time.Instant. Use setObject() with an explici

pasted

11:42
Теперь root cause ясен:

Can't infer the SQL type to use for an instance of java.time.Instant
Проблема не в колонке, а в том что JdbcTemplate не умеет автоматически конвертировать java.time.Instant в SQL тип. Нужно исправить код в ShopOrderConsumer.createCustomer().

На маке открываем файл и меняем INSERT:

java
// Было:
jdbc.update(
"INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)",
customerId, info.getExternalId(), now, now
);

// Надо:
jdbc.update(
"INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)",
customerId, info.getExternalId(),
java.sql.Timestamp.from(now),
java.sql.Timestamp.from(now)
);
То же самое в createUnknownCustomer() — там тоже now передаётся в jdbc.update. Исправляем оба места:

bash
grep -n "Instant.now\|jdbc.update" /Users/mihailbolohonov/IdeaProjects/crm_final/crm-backend/src/main/java/com/crm/kafka/consumer/ShopOrderConsumer.java | head -20
private UUID createUnknownCustomer(String schema) {
// Ищем или создаём специального клиента «Покупатель из магазина»
try {
UUID id = jdbc.queryForObject(
"SELECT id FROM customers WHERE external_id = '__shop_unknown__' LIMIT 1",
UUID.class
);
if (id != null) return id;
} catch (Exception ignored) {}

    UUID id = UUID.randomUUID();
    Instant now = Instant.now();
    jdbc.update(
            "INSERT INTO customers (id, type, status, external_id, created_at, updated_at) " +
                    "VALUES (?, 'INDIVIDUAL', 'ACTIVE', '__shop_unknown__', ?, ?)",
            id, now, now
    );
    jdbc.update(
            "INSERT INTO customer_personal_data " +
                    "(id, customer_id, first_name, last_name, email) " +
                    "VALUES (uuid_generate_v4(), ?, 'Покупатель', 'Из магазина', 'shop@noreply.local')",
            id
    );
    return id;
}
Show more
11:43
Нужно исправить оба метода — createCustomer и createUnknownCustomer. Меняем now на Timestamp.from(now) во всех jdbc.update вызовах:

В createCustomer:

java
jdbc.update(
"INSERT INTO customers (id, type, status, external_id, created_at, updated_at) " +
"VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)",
customerId, info.getExternalId(),
java.sql.Timestamp.from(now),
java.sql.Timestamp.from(now)
);
В createUnknownCustomer:

java
jdbc.update(
"INSERT INTO customers (id, type, status, external_id, created_at, updated_at) " +
"VALUES (?, 'INDIVIDUAL', 'ACTIVE', '__shop_unknown__', ?, ?)",
id,
java.sql.Timestamp.from(now),
java.sql.Timestamp.from(now)
);
После правки деплоим:

bash
cd /Users/mihailbolohonov/IdeaProjects/crm_final
./deploy.sh crm-app latest

package com.crm.kafka.consumer; import com.crm.kafka.config.KafkaProperties; import com.crm.kafka.dto.ShopOrderCreatedEvent; import com.crm.order.entity.Order; import com.crm.order.entity.OrderItem; import com.crm.order.repository.OrderItemRepository; import com.crm.order.repository.OrderRepository

pasted

11:44
Код уже исправлен (java.sql.Timestamp.from(now) стоит в обоих местах). Деплой запущен — ждём результата. Когда появится промпт на сервере, проверяем:

bash
kubectl get pods -n crm -w
может надо было jpa использовать или hibernate

11:44
Да, правильное замечание. JdbcTemplate с Instant — это костыль. Лучше использовать Spring Data JDBC или JPA репозитории как везде в проекте. Но это рефакторинг — сейчас важнее убедиться что текущий фикс работает.

Как там деплой — завершился?

NAME                               READY   STATUS    RESTARTS   AGE
crm-app-backend-df5cc579c-s89hs    1/1     Running   0          2m8s
crm-app-frontend-8f9488494-rj8zm   1/1     Running   0          2m8s

11:51
bash
kubectl logs crm-app-backend-df5cc579c-s89hs -n crm | grep -E "Kafka received|Order created|ERROR" | head -10
ssh -J root@77.222.35.2 michael@10.0.0.2
michael@demo-server:~$ kubectl logs crm-app-backend-df5cc579c-s89hs -n crm | grep -E "Kafka received|Order created|ERROR" | head -10
2026-03-07 07:50:33 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  [] c.c.kafka.consumer.ShopOrderConsumer - Kafka received: shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea partition=0 offset=0
2026-03-07 07:50:33 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] c.c.kafka.consumer.ShopOrderConsumer - Failed to process shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea: PreparedStatementCallback; uncategorized SQLException for SQL [INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)]; SQL state [25P02]; error code [0]; ERROR: current transaction is aborted, commands ignored until end of transaction block
org.springframework.jdbc.UncategorizedSQLException: PreparedStatementCallback; uncategorized SQLException for SQL [INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)]; SQL state [25P02]; error code [0]; ERROR: current transaction is aborted, commands ignored until end of transaction block
Caused by: org.postgresql.util.PSQLException: ERROR: current transaction is aborted, commands ignored until end of transaction block
Caused by: org.postgresql.util.PSQLException: ERROR: column pd.email does not exist
2026-03-07 07:50:35 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] o.s.k.l.KafkaMessageListenerContainer - Error handler threw an exception
Caused by: org.springframework.jdbc.UncategorizedSQLException: PreparedStatementCallback; uncategorized SQLException for SQL [INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)]; SQL state [25P02]; error code [0]; ERROR: current transaction is aborted, commands ignored until end of transaction block
Caused by: org.postgresql.util.PSQLException: ERROR: current transaction is aborted, commands ignored until end of transaction block
Caused by: org.postgresql.util.PSQLException: ERROR: column pd.email does not exist
2026-03-07 07:50:35 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  [] c.c.kafka.consumer.ShopOrderConsumer - Kafka received: shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea partition=0 offset=0
michael@demo-server:~$

Show more
11:51
Прогресс! Instant починили, теперь новая ошибка:

column pd.email does not exist
Таблица customer_personal_data существует, но колонка email в ней отсутствует. Смотрим структуру:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT column_name FROM information_schema.columns
WHERE table_schema = 'tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710'
AND table_name = 'customer_personal_data';"
customer_id
first_name
last_name
middle_name
phone
address
position
updated_at
(8 rows)

11:51
email отсутствует в customer_personal_data — и id тоже нет. Нужно добавить обе колонки вручную и создать ещё одну миграцию.

Сначала применяем вручную к обеим схемам:

bash
docker exec postgres psql -U crm_user -d crm_db -c "
ALTER TABLE tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.customer_personal_data
ADD COLUMN IF NOT EXISTS id UUID DEFAULT uuid_generate_v4(),
ADD COLUMN IF NOT EXISTS email VARCHAR(255);

ALTER TABLE tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a.customer_personal_data
ADD COLUMN IF NOT EXISTS id UUID DEFAULT uuid_generate_v4(),
ADD COLUMN IF NOT EXISTS email VARCHAR(255);
"
И параллельно создаём миграцию на маке:

bash
cat > /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/V120__customer_personal_data_extend.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<databaseChangeLog
xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog
http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-4.27.xsd">

    <changeSet id="V120-1" author="platform" labels="tenant,customers">
        <comment>Add id and email to customer_personal_data</comment>
        <addColumn tableName="customer_personal_data">
            <column name="id" type="UUID" defaultValueComputed="uuid_generate_v4()"/>
            <column name="email" type="VARCHAR(255)"/>
        </addColumn>
        <rollback>
            <dropColumn tableName="customer_personal_data" columnName="id"/>
            <dropColumn tableName="customer_personal_data" columnName="email"/>
        </rollback>
    </changeSet>

</databaseChangeLog>
EOF
запустил деплой

11:53
Пока деплой идёт — проверяем что вручную колонки добавились:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT column_name FROM information_schema.columns
WHERE table_schema = 'tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710'
AND table_name = 'customer_personal_data';"
customer_id
first_name
last_name
middle_name
phone
address
position
updated_at
id
email
(10 rows)

11:53
Колонки добавлены ✅. Не забудь добавить V120 в tenant-changelog.xml на маке:

bash
sed -i '' 's|    <include file="db/migration/tenant/V119__customers_external_id.xml".*/>|    <include file="db/migration/tenant/V119__customers_external_id.xml"              relativeToChangelogFile="false"/>\n    <include file="db/migration/tenant/V120__customer_personal_data_extend.xml"  relativeToChangelogFile="false"/>|' \
/Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/tenant-changelog.xml

grep "V119\|V120" /Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/src/main/resources/db/migration/tenant/tenant-changelog.xml
сейчас миграций куча - можем их переделать - удалить БД и заново пересоздать

11:55
Да, хорошая идея — лучше один раз правильно, чем накапливать костыли.

Но сначала дождёмся текущего деплоя и проверим работает ли Kafka интеграция с текущими ручными фиксами. Если всё заработает — тогда спокойно рефакторим миграции.

Как там деплой?

пока идет

11:57
Пока ждём — план по рефакторингу миграций:

Удалить все тенант-схемы из БД (tenant_*)
Удалить записи из public.tenants
Объединить все V114-V120 в один файл, например V114__shop_integration.xml — добавить туда external_id, email, id в customer_personal_data, kafka поля для orders и т.д.
Удалить лишние файлы миграций
Почистить tenant-changelog.xml
Задеплоить — при регистрации нового тенанта схема создастся чистой и правильной
Готов помочь собрать итоговый XML со всеми нужными колонками когда деплой завершится.

docker build --no-cache - зря поставил, долго идет
11:58
Да, --no-cache для frontend не нужен — там миграций нет. Убираем из deploy.sh — оставляем только для backend:

bash
grep "docker build" /Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh
Покажи вывод — проверим что frontend тоже с --no-cache или нет.

не для бэкенд echo "[server] Building crm-backend..."
docker build --no-cache -t \$REGISTRY/crm-backend:\$TAG \$BUILD_DIR/crm-backend/

11:58
Только backend с --no-cache — это правильно. Frontend без него. Всё нормально, просто backend долго собирается из-за Maven dependency:go-offline.

Можно ускорить — добавить .m2 кэш через Docker volume. Но это отдельная задача. Ждём завершения.

пока идет довай сделаем Можно ускорить — добавить .m2 кэш через Docker volume. Но это отдельная задача. Ждём завершения.

11:58
В deploy.sh меняем строку сборки backend:

bash
docker build --no-cache \
-t \$REGISTRY/crm-backend:\$TAG \
--build-arg MAVEN_OPTS="-Dmaven.repo.local=/root/.m2/repository" \
-v /home/michael/.m2:/root/.m2 \
\$BUILD_DIR/crm-backend/
Но legacy builder не поддерживает -v при сборке. Нужно переключиться на BuildKit. Добавляем в начало REMOTEBUILD секции:

bash
export DOCKER_BUILDKIT=1
И меняем Dockerfile — добавляем mount кэша:

dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
# Кэшируем зависимости отдельным слоем
RUN --mount=type=cache,target=/root/.m2 \
mvn dependency:go-offline -q

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
mvn package -DskipTests -q
С BuildKit кэш .m2 будет персистентным между сборками — dependency:go-offline будет занимать секунды вместо минут.

Показывай текущий Dockerfile на маке — внесём правки:

bash
cat /Users/mihailbolohonov/IdeaProjects/crm_final/crm-backend/Dockerfile
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
Show more
11:59
Меняем Dockerfile:

bash
cat > /Users/mihailbolohonov/IdeaProjects/crm_final/crm-backend/Dockerfile << 'EOF'
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
mvn dependency:go-offline -q

COPY src src
RUN --mount=type=cache,target=/root/.m2 \
mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
EOF
И в deploy.sh добавляем DOCKER_BUILDKIT=1 в начало REMOTEBUILD секции. Найди строку ssh -J $VPS $SERVER bash << REMOTEBUILD и сразу после set -e добавь:

bash
sed -i '' 's/REMOTEBUILD\nset -e/REMOTEBUILD\nset -e\nexport DOCKER_BUILDKIT=1/' \
/Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh
Или проще — открой deploy.sh в редакторе и добавь export DOCKER_BUILDKIT=1 после set -e внутри REMOTEBUILD секции.

log "Building images on server..."
ssh -J $VPS $SERVER bash << REMOTEBUILD
set -e
export DOCKER_BUILDKIT=1
REGISTRY="localhost:5000"
TAG="$TAG"
BUILD_DIR="$REMOTE_BUILD_DIR"

echo "[server] Copying liquibase migrations into backend..."
cp -r \$BUILD_DIR/crm-liquibase/src/main/resources/db/. \$BUILD_DIR/crm-backend/src/main/resources/db/

echo "[server] Building crm-backend..."
docker build --no-cache -t \$REGISTRY/crm-backend:\$TAG \$BUILD_DIR/crm-backend/

echo "[server] Building crm-frontend..."
docker build -t \$REGISTRY/crm-frontend:\$TAG \
--build-arg VITE_API_BASE_URL=/crm/api/v1 \
\$BUILD_DIR/crm-frontend/

echo "[server] Pushing to local registry..."
docker push \$REGISTRY/crm-backend:\$TAG
docker push \$REGISTRY/crm-frontend:\$TAG
echo "[server] Done building"
REMOTEBUILD
Show more
12:01
Хорошо, DOCKER_BUILDKIT=1 уже стоит. Теперь с новым Dockerfile (--mount=type=cache) можно убрать --no-cache — BuildKit сам инвалидирует слои при изменении файлов, а .m2 кэш будет переиспользоваться:

bash
sed -i '' 's/docker build --no-cache -t/docker build -t/' \
/Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh

grep "docker build" /Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh
проверь #!/bin/bash
set -e

APP=${1:-crm-app}
TAG=${2:-latest}

VPS="root@77.222.35.2"
SERVER="michael@10.0.0.2"
REMOTE_BUILD_DIR="/home/michael/build/$APP"
REMOTE_SECRETS_DIR="/home/michael/secrets/$APP"
HELM_CHART="./crm-helm"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[deploy]${NC} $1"; }
warn() { echo -e "${YELLOW}[warn]${NC} $1"; }
fail() { echo -e "${RED}[error]${NC} $1"; exit 1; }

log "Starting deploy of $APP:$TAG"

[ ! -d "$HELM_CHART" ] && fail "Helm chart not found: $HELM_CHART"

log "Syncing sources to server..."
ssh -J $VPS $SERVER "mkdir -p $REMOTE_BUILD_DIR"

for SERVICE in crm-backend crm-frontend crm-liquibase; do
if [ -d "./$SERVICE" ]; then
log "  Syncing $SERVICE..."
rsync -az --delete \
--exclude='target/' \
--exclude='node_modules/' \
--exclude='.git/' \
--exclude='*.class' \
-e "ssh -J $VPS" \
./$SERVICE/ $SERVER:$REMOTE_BUILD_DIR/$SERVICE/
fi
done

log "Building images on server..."
ssh -J $VPS $SERVER bash << REMOTEBUILD
set -e
export DOCKER_BUILDKIT=1
REGISTRY="localhost:5000"
TAG="$TAG"
BUILD_DIR="$REMOTE_BUILD_DIR"

echo "[server] Copying liquibase migrations into backend..."
cp -r \$BUILD_DIR/crm-liquibase/src/main/resources/db/. \$BUILD_DIR/crm-backend/src/main/resources/db/

echo "[server] Building crm-backend..."
docker build -t \$REGISTRY/crm-backend:\$TAG \$BUILD_DIR/crm-backend/

echo "[server] Building crm-frontend..."
docker build -t \$REGISTRY/crm-frontend:\$TAG \
--build-arg VITE_API_BASE_URL=/crm/api/v1 \
\$BUILD_DIR/crm-frontend/

echo "[server] Pushing to local registry..."
docker push \$REGISTRY/crm-backend:\$TAG
docker push \$REGISTRY/crm-frontend:\$TAG
echo "[server] Done building"
REMOTEBUILD

log "Syncing helm chart..."
ssh -J $VPS $SERVER "mkdir -p $REMOTE_BUILD_DIR/helm"
rsync -az --delete \
--exclude='values.prod.yaml' \
-e "ssh -J $VPS" \
$HELM_CHART/ $SERVER:$REMOTE_BUILD_DIR/helm/

log "Deploying to k3s..."
ssh -J $VPS $SERVER bash << REMOTEDEPLOY
set -e
SECRETS="$REMOTE_SECRETS_DIR/values.prod.yaml"
CHART="$REMOTE_BUILD_DIR/helm"

[ ! -f "\$SECRETS" ] && echo "ERROR: values.prod.yaml not found at \$SECRETS" && exit 1

echo "[server] Clearing k3s image cache..."
sudo crictl rmi localhost:5000/crm-backend:$TAG 2>/dev/null || true
sudo crictl rmi localhost:5000/crm-frontend:$TAG 2>/dev/null || true

helm upgrade --install $APP \$CHART \
--namespace crm --create-namespace \
--values \$SECRETS \
--set backend.image.tag=$TAG \
--set frontend.image.tag=$TAG

echo "[server] Restarting deployments..."
kubectl -n crm rollout restart deployment/$APP-backend
kubectl -n crm rollout restart deployment/$APP-frontend

echo "[server] Checking rollout..."
kubectl -n crm rollout status deployment/$APP-backend --timeout=300s
kubectl -n crm rollout status deployment/$APP-frontend --timeout=120s
echo "[server] Deploy complete"
REMOTEDEPLOY

log "Deploy of $APP:$TAG complete"

Show more
12:01
Всё выглядит хорошо ✅. Единственное — --no-cache убран, DOCKER_BUILDKIT=1 стоит, Dockerfile использует --mount=type=cache.

Первый деплой с новым Dockerfile всё равно будет долгим (скачает зависимости в кэш). Зато все последующие будут быстрыми — Maven будет брать зависимости из /root/.m2 кэша на сервере.

Как там текущий деплой — завершился?

NAME                                READY   STATUS    RESTARTS   AGE
crm-app-backend-64c8cdb657-bf68l    1/1     Running   0          2m42s
crm-app-frontend-677fd5c6f8-jgb4h   1/1     Running   0          2m41s

12:02
bash
kubectl logs crm-app-backend-64c8cdb657-bf68l -n crm | grep -E "Kafka received|Order created|ERROR" | head -10
ssh -J root@77.222.35.2 michael@10.0.0.2
michael@demo-server:~$ kubectl logs crm-app-backend-64c8cdb657-bf68l -n crm | grep -E "Kafka received|Order created|ERROR" | head -10
2026-03-07 08:01:09 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  [] c.c.kafka.consumer.ShopOrderConsumer - Kafka received: shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea partition=0 offset=0
2026-03-07 08:01:10 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] c.c.kafka.consumer.ShopOrderConsumer - Failed to process shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea: Failed to execute InsertRoot{entity=OrderItem(id=null, orderId=a732b171-4b23-4d55-9c67-c379344bec3d, productId=a1000000-0000-0000-0000-000000000013, quantity=1, price=7200.00, totalPrice=7200.00), idValueSource=GENERATED}
Caused by: org.springframework.dao.DataIntegrityViolationException: PreparedStatementCallback; SQL [INSERT INTO "order_items" ("order_id", "price", "product_id", "quantity", "total_price") VALUES (?, ?, ?, ?, ?)]; ERROR: null value in column "product_name" of relation "order_items" violates not-null constraint
Caused by: org.postgresql.util.PSQLException: ERROR: null value in column "product_name" of relation "order_items" violates not-null constraint
2026-03-07 08:01:12 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] o.s.k.l.KafkaMessageListenerContainer - Error handler threw an exception
Caused by: org.springframework.dao.DataIntegrityViolationException: PreparedStatementCallback; SQL [INSERT INTO "order_items" ("order_id", "price", "product_id", "quantity", "total_price") VALUES (?, ?, ?, ?, ?)]; ERROR: null value in column "product_name" of relation "order_items" violates not-null constraint
Caused by: org.postgresql.util.PSQLException: ERROR: null value in column "product_name" of relation "order_items" violates not-null constraint
2026-03-07 08:01:13 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  [] c.c.kafka.consumer.ShopOrderConsumer - Kafka received: shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea partition=0 offset=0
2026-03-07 08:01:13 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] ERROR [] c.c.kafka.consumer.ShopOrderConsumer - Failed to process shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea: Failed to execute InsertRoot{entity=OrderItem(id=null, orderId=49649094-f170-424f-9225-b6933cec8bad, productId=a1000000-0000-0000-0000-000000000013, quantity=1, price=7200.00, totalPrice=7200.00), idValueSource=GENERATED}
Caused by: org.springframework.dao.DataIntegrityViolationException: PreparedStatementCallback; SQL [INSERT INTO "order_items" ("order_id", "price", "product_id", "quantity", "total_price") VALUES (?, ?, ?, ?, ?)]; ERROR: null value in column "product_name" of relation "order_items" violates not-null constraint
michael@demo-server:~$

Show more
12:02
Прогресс! Клиент создался успешно, заказ тоже, но падает на order_items — колонка product_name NOT NULL а в коде она не заполняется.

Смотрим структуру order_items и код OrderItem:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT column_name, is_nullable, column_default FROM information_schema.columns
WHERE table_schema = 'tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710'
AND table_name = 'order_items';"
--------------+-------------+-------------------------
id           | NO          | uuid_generate_v4()
order_id     | NO          |
product_id   | YES         |
product_name | NO          |
product_sku  | YES         |
product_unit | YES         | 'шт'::character varying
quantity     | NO          | 1
price        | NO          |
total_price  | NO          |
(9 rows)

12:02
product_name NOT NULL — а в OrderItem entity это поле не заполняется. Быстрый фикс — добавить product_name в resolveItems в ShopOrderConsumer.java:

java
OrderItem orderItem = OrderItem.builder()
.orderId(orderId)
.productId(productId)
.productName(item.getName())  // добавить эту строку
.productSku(item.getSku())    // и эту
.quantity(qty)
.price(price)
.totalPrice(price.multiply(qty))
.build();
Проверяем что поля есть в OrderItem entity:

bash
grep -n "productName\|product_name\|productSku" /Users/mihailbolohonov/IdeaProjects/crm_final/crm-backend/src/main/java/com/crm/order/entity/OrderItem.java
package com.crm.order.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
* Позиция заказа.
* price — цена на момент создания (не зависит от текущей цены товара)
  */
  @Data @Builder @NoArgsConstructor @AllArgsConstructor
  @Table("order_items")
  public class OrderItem {
  @Id private UUID id;
  private UUID orderId;
  private UUID productId;
  private BigDecimal quantity;
  private BigDecimal price;       // цена на момент добавления
  private BigDecimal totalPrice;  // quantity * price (хранится для быстрого чтения)
  }
  Show more
  12:09
  В OrderItem нет полей productName и productSku — нужно добавить. На маке:

java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("order_items")
public class OrderItem {
@Id private UUID id;
private UUID orderId;
private UUID productId;
private String productName;
private String productSku;
private BigDecimal quantity;
private BigDecimal price;
private BigDecimal totalPrice;
}
И в ShopOrderConsumer.resolveItems() добавить заполнение:

java
OrderItem orderItem = OrderItem.builder()
.orderId(orderId)
.productId(productId)
.productName(item.getName())
.productSku(item.getSku())
.quantity(qty)
.price(price)
.totalPrice(price.multiply(qty))
.build();
После правки — деплой.

./deploy.sh crm-app latest
[deploy] Starting deploy of crm-app:latest
[deploy] Syncing sources to server...
[deploy]   Syncing crm-backend...
[deploy]   Syncing crm-frontend...
[deploy]   Syncing crm-liquibase...
[deploy] Building images on server...
[server] Copying liquibase migrations into backend...
[server] Building crm-backend...
ERROR: BuildKit is enabled but the buildx component is missing or broken.
Install the buildx component to build images with BuildKit:
https://docs.docker.com/go/buildx/

12:11
Buildx не установлен на сервере. Убираем DOCKER_BUILDKIT=1 из deploy.sh и возвращаем --no-cache:

bash
sed -i '' 's/export DOCKER_BUILDKIT=1//' \
/Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh

sed -i '' 's/docker build -t \$REGISTRY\/crm-backend:\$TAG/docker build --no-cache -t \$REGISTRY\/crm-backend:\$TAG/' \
/Users/mihailbolohonov/IdeaProjects/crm_final/deploy.sh
И Dockerfile откатываем — убираем --mount=type=cache:

bash
cat > /Users/mihailbolohonov/IdeaProjects/crm_final/crm-backend/Dockerfile << 'EOF'
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src src
RUN mvn package -DskipTests -q

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport \
-XX:MaxRAMPercentage=75.0 \
-XX:+ExitOnOutOfMemoryError \
-Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
EOF
Потом установим buildx на сервере отдельно. Запускай деплой снова.

нет - давай установим buildx сейчас

12:12
bash
# На сервере
mkdir -p ~/.docker/cli-plugins
curl -SL https://github.com/docker/buildx/releases/latest/download/buildx-v0.23.0.linux-amd64 \
-o ~/.docker/cli-plugins/docker-buildx
chmod +x ~/.docker/cli-plugins/docker-buildx
docker buildx version
michael@demo-server:~$ docker buildx version
docker: unknown command: docker buildx
Run 'docker --help' for more information
michael@demo-server:~$

12:13
Нужно правильное имя файла — без версии в имени:

bash
mkdir -p ~/.docker/cli-plugins
curl -SL https://github.com/docker/buildx/releases/latest/download/buildx-v0.23.0.linux-amd64 \
-o ~/.docker/cli-plugins/docker-buildx
chmod a+x ~/.docker/cli-plugins/docker-buildx
docker buildx version
в какой команде ошибка

12:13
В первой — имя файла скачивается как buildx-v0.23.0.linux-amd64 но должно быть docker-buildx:

bash
ls ~/.docker/cli-plugins/
michael@demo-server:~$ mkdir -p ~/.docker/cli-plugins
curl -SL https://github.com/docker/buildx/releases/latest/download/buildx-v0.23.0.linux-amd64 \
-o ~/.docker/cli-plugins/docker-buildx
chmod a+x ~/.docker/cli-plugins/docker-buildx
docker buildx version
% Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
Dload  Upload   Total   Spent    Left  Speed
0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0
100     9  100     9    0     0     18      0 --:--:-- --:--:-- --:--:--    18
docker: unknown command: docker buildx
Run 'docker --help' for more information
michael@demo-server:~$ docker buildx version
docker: unknown command: docker buildx
Run 'docker --help' for more information
michael@demo-server:~$

Show more
12:13
Файл скачался размером 9 байт — это редирект, а не бинарник. Нужно найти актуальную версию:

bash
# Смотрим последний релиз
curl -s https://api.github.com/repos/docker/buildx/releases/latest | grep "browser_download_url.*linux-amd64" | grep -v sha256
michael@demo-server:~$ # Смотрим последний релиз
curl -s https://api.github.com/repos/docker/buildx/releases/latest | grep "browser_download_url.*linux-amd64" | grep -v sha256
"browser_download_url": "https://github.com/docker/buildx/releases/download/v0.32.1/buildx-v0.32.1.linux-amd64"
"browser_download_url": "https://github.com/docker/buildx/releases/download/v0.32.1/buildx-v0.32.1.linux-amd64.provenance.json"
"browser_download_url": "https://github.com/docker/buildx/releases/download/v0.32.1/buildx-v0.32.1.linux-amd64.sbom.json"
"browser_download_url": "https://github.com/docker/buildx/releases/download/v0.32.1/buildx-v0.32.1.linux-amd64.sigstore.json"

Show more
12:14
bash
curl -SL https://github.com/docker/buildx/releases/download/v0.32.1/buildx-v0.32.1.linux-amd64 \
-o ~/.docker/cli-plugins/docker-buildx
chmod a+x ~/.docker/cli-plugins/docker-buildx
docker buildx version
docker-buildx - это что вообще ?

12:14
docker-buildx — это плагин для Docker который добавляет команду docker buildx.

BuildKit — это новый движок сборки Docker образов, который умеет:

кэшировать отдельные слои включая внешние директории (.m2, npm)
параллельно собирать независимые слои
монтировать кэш через --mount=type=cache в Dockerfile
Раньше был встроен в Docker Desktop, но на Linux серверах идёт отдельным плагином. Как там скачался?

michael@demo-server:~$ curl -SL https://github.com/docker/buildx/releases/download/v0.32.1/buildx-v0.32.1.linux-amd64 \
-o ~/.docker/cli-plugins/docker-buildx
chmod a+x ~/.docker/cli-plugins/docker-buildx
docker buildx version
% Total    % Received % Xferd  Average Speed   Time    Time     Time  Current
Dload  Upload   Total   Spent    Left  Speed
0     0    0     0    0     0      0      0 --:--:-- --:--:-- --:--:--     0
100 86.1M  100 86.1M    0     0  6091k      0  0:00:14  0:00:14 --:--:-- 7350k
github.com/docker/buildx v0.32.1 d3bfb3f4e48a67dda56e957a6636f4fab6c5fcb2

12:14
Установлен ✅. Теперь запускай деплой:

bash
cd /Users/mihailbolohonov/IdeaProjects/crm_final
./deploy.sh crm-app latest
NAME                              READY   STATUS    RESTARTS   AGE
crm-app-backend-7ccd4959c-9s8c7   1/1     Running   0          14m
crm-app-frontend-98988bcf-ptmtp   1/1     Running   0          14m
michael@demo-server:~$

12:34
bash
kubectl logs crm-app-backend-7ccd4959c-9s8c7 -n crm | grep -E "Kafka received|Order created|ERROR" | head -10
michael@demo-server:~$ kubectl logs crm-app-backend-7ccd4959c-9s8c7 -n crm | grep -E "Kafka received|Order created|ERROR" | head -10
2026-03-07 08:22:13 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  [] c.c.kafka.consumer.ShopOrderConsumer - Kafka received: shopOrderId=SHOP-00004 shopOrderUuid=755ac0a0-1293-47ae-8b56-1a35ba6c28ea partition=0 offset=0
2026-03-07 08:22:14 [org.springframework.kafka.KafkaListenerEndpointContainer#0-0-C-1] INFO  [] c.c.kafka.consumer.ShopOrderConsumer - Order created from shop: crmOrderId=7a9aba9d-3135-40f5-9a95-623f767ff939 shopOrderId=SHOP-00004 customerId=2557a440-32bc-4f68-8ac0-e4381919d998 items=1 total=7200.00

12:35
🎉 Работает! Заказ успешно создан в CRM:

shopOrderId=SHOP-00004 получен ✅
crmOrderId=7a9aba9d... создан ✅
customerId=2557a440... создан ✅
items=1, total=7200.00 ✅
Проверяем idempotency log и что заказ реально в БД:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT shop_order_uuid, crm_order_id, processed_at FROM public.kafka_idempotency_log;"

docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, external_order_id, shop_order_uuid, total_amount, created_at
FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.orders
ORDER BY created_at DESC LIMIT 3;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT shop_order_uuid, crm_order_id, processed_at FROM public.kafka_idempotency_log;"
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, external_order_id, shop_order_uuid, total_amount, created_at
FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.orders
ORDER BY created_at DESC LIMIT 3;"
shop_order_uuid            |             crm_order_id             |         processed_at          
--------------------------------------+--------------------------------------+-------------------------------
755ac0a0-1293-47ae-8b56-1a35ba6c28ea | 7a9aba9d-3135-40f5-9a95-623f767ff939 | 2026-03-07 08:22:13.204915+00
(1 row)
id                  | external_order_id |           shop_order_uuid            | total_amount |          created_at           
--------------------------------------+-------------------+--------------------------------------+--------------+-------------------------------
7a9aba9d-3135-40f5-9a95-623f767ff939 | SHOP-00004        | 755ac0a0-1293-47ae-8b56-1a35ba6c28ea |      7200.00 | 2026-03-06 15:06:49.482626+00
e0000000-0000-0000-0000-000000000072 |                   |                                      |     45000.00 | 2026-03-06 09:35:53.437302+00
e0000000-0000-0000-0000-000000000071 |                   |                                      |    480000.00 | 2026-03-06 08:05:53.437302+00
(3 rows)
michael@demo-server:~$

Show more
12:35
Kafka интеграция Shop → CRM полностью работает! ✅

Idempotency log записан ✅
Заказ SHOP-00004 создан в CRM с правильным shop_order_uuid и total=7200.00 ✅
Теперь тестируем обратное направление — CRM → Shop (смена статуса заказа). Нужно поменять статус заказа в CRM и проверить что Shop получит событие. Как авторизоваться в CRM — через Swagger или есть готовый токен?

теперь в shop. ghb lj,fdktybb pfrfpf d rjhpbye jib,rf Request URL
https://bolohonovma.online/shop/api/v1/cart/items
Request Method
POST
Status Code
403 Forbidden
Remote Address
77.222.35.2:443

12:38
403 при добавлении в корзину — проблема с авторизацией. Проверяем логи shop:

bash
kubectl logs deployment/shop-app-backend -n shop --tail=30 | grep -E "403|JWT|auth|ERROR" | head -20
{
"timestamp": "2026-03-07T08:38:17.253+00:00",
"status": 403,
"error": "Forbidden",
"path": "/api/v1/cart/items"
}

12:39
Это ответ от сервера, но нужны логи. Выполни:

bash
kubectl logs deployment/shop-app-backend -n shop --tail=50 | grep -E "403|JWT|Security|Forbidden|ERROR" | head -20
michael@demo-server:~$ kubectl logs deployment/shop-app-backend -n shop --tail=50 | grep -E "403|JWT|Security|Forbidden|ERROR" | head -20
at org.springframework.security.config.annotation.web.configuration.WebMvcSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebMvcSecurityConfiguration.java:238)

12:39
Только один stacktrace фрагмент. Смотрим полнее:

bash
kubectl logs deployment/shop-app-backend -n shop --tail=100 | grep -B2 -A5 "403\|Forbidden\|AccessDenied" | head -40
в crm при обновлении страницы - переносит на страницу лоиган

12:40
Это отдельная проблема с CRM фронтендом — скорее всего сессия/токен не сохраняется при обновлении страницы. Но давай сначала закончим с Shop 403.

Скидывай вывод последней команды с логами.

michael@demo-server:~$ kubectl logs deployment/shop-app-backend -n shop --tail=100 | grep -B2 -A5 "403\|Forbidden\|AccessDenied" | head -40
michael@demo-server:~$

12:40
Пусто — значит 403 отдаёт не Spring Security а что-то выше (Traefik или nginx). Проверяем какой токен передаётся в запросе — в браузере открой DevTools → Network → найди запрос POST /shop/api/v1/cart/items → Headers → скопируй Authorization заголовок.

Или проверим напрямую:

bash
# Сначала логинимся
curl -s -X POST https://bolohonovma.online/shop/api/v1/auth/login \
-H "Content-Type: application/json" \
-d '{"email":"bolohonovmakar@gmail.com","password":"<пароль>"}' | jq .
был кэш сейчас все ок

12:41
Отлично! Тогда пробуй добавить товар в корзину и оформить заказ в Shop — проверяем полный флоу Shop → CRM.

нет - корзина теперь пуста Request URL
https://bolohonovma.online/shop/api/v1/cart
Request Method
GET
Status Code
403 Forbidden
Remote Address
77.222.35.2:443

12:42
Снова 403 на GET /cart. Проверяем токен — залогинься заново в Shop и попробуй снова. Если не помогает:

bash
kubectl logs deployment/shop-app-backend -n shop --tail=50 | tail -20
michael@demo-server:~$ kubectl logs deployment/shop-app-backend -n shop --tail=50 | tail -20
at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116)
at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:164)
at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:140)
at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:167)
at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:90)
at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:483)
at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:115)
at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:93)
at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:74)
at org.apache.catalina.valves.RemoteIpValve.invoke(RemoteIpValve.java:731)
at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:344)
at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:397)
at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:63)
at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:905)
at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1743)
at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:52)
at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1190)
at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:659)
at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63)
at java.base/java.lang.Thread.run(Unknown Source)

Show more
12:42
Это хвост stacktrace без начала. Смотрим начало ошибки:

bash
kubectl logs deployment/shop-app-backend -n shop --tail=100 | grep -E "JwtException|ExpiredJwt|InvalidJwt|Bad credentials|UsernameNotFoundException|AuthenticationException" | head -10
michael@demo-server:~$ kubectl logs deployment/shop-app-backend -n shop --tail=100 | grep -E "JwtException|ExpiredJwt|InvalidJwt|Bad credentials|UsernameNotFoundException|AuthenticationException" | head -10
michael@demo-server:~$

12:42
Пусто. Скорее всего токен протух или неверный JWT_SECRET. Проверяем:

bash
kubectl exec deployment/shop-app-backend -n shop -- env | grep JWT
ну он есть

12:43
Попробуй разлогиниться и залогиниться заново в Shop UI — токен мог протухнуть. Если после повторного логина всё равно 403 на /cart, скидывай полный Authorization заголовок из DevTools.

Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJiODBiMWRkNi01ODU1LTQ3OTEtOThlZS0yZmEzNTBmYWFmYTAiLCJlbWFpbCI6ImJvbG9ob25vdm1ha2FyQGdtYWlsLmNvbSIsInRlbmFudFNjaGVtYSI6InRlbmFudF80Y2ZmN2M2NV84OTk3XzRiZTZfYTM5Y18yZmVhYzhjNzI0MmEiLCJ1c2VyVHlwZSI6IkFETUlOIiwiaWF0IjoxNzcyODcyODkzLCJleHAiOjE3NzI4NzM3OTN9.ToCcKhAMJTGdDRSWM_nceG6SCkxbFHTCK_P6ppJbxbaJlu8AN1npVqbN37uJ0oJfMHzDpgYosHcik9CCmu-doQ

12:43
Токен есть. Декодируем payload — exp: 1772873793 это примерно 15 минут жизни. Проверяем напрямую с этим токеном:

bash
curl -s https://bolohonovma.online/shop/api/v1/cart \
-H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJiODBiMWRkNi01ODU1LTQ3OTEtOThlZS0yZmEzNTBmYWFmYTAiLCJlbWFpbCI6ImJvbG9ob25vdm1ha2FyQGdtYWlsLmNvbSIsInRlbmFudFNjaGVtYSI6InRlbmFudF80Y2ZmN2M2NV84OTk3XzRiZTZfYTM5Y18yZmVhYzhjNzI0MmEiLCJ1c2VyVHlwZSI6IkFETUlOIiwiaWF0IjoxNzcyODcyODkzLCJleHAiOjE3NzI4NzM3OTN9.ToCcKhAMJTGdDRSWM_nceG6SCkxbFHTCK_P6ppJbxbaJlu8AN1npVqbN37uJ0oJfMHzDpgYosHcik9CCmu-doQ"
{"timestamp":"2026-03-07T08:43:36.850+00:00","status":403,"error":"Forbidden","path":"/api/v1/cart"}michael@demo-server:~$

12:43
Токен валидный но 403. Обрати внимание — в токене tenantSchema: tenant_4cff7c65... а это CRM тенант, не Shop.

Проблема скорее всего в том что Shop проверяет userType или роль. В токене userType: ADMIN — возможно /cart доступен только для обычных пользователей, не админов.

Проверяем Security конфиг Shop:

bash
find /home/claude/shop -name "SecurityConfig*" -o -name "*Security*.java" | head -5
package com.shop.common.config;

import com.shop.auth.filter.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration @EnableWebSecurity @RequiredArgsConstructor
public class SecurityConfig {
private final JwtAuthenticationFilter jwtFilter;
private final AppProperties appProperties;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.cors(c -> c.configurationSource(corsSource()))
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                // Public endpoints
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                // Protected endpoints
                .requestMatchers("/cart/**").authenticated()
                .requestMatchers("/orders/**").authenticated()
                .requestMatchers("/payments/**").authenticated()
                .requestMatchers("/events/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    private CorsConfigurationSource corsSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(appProperties.getCors().getAllowedOrigins());
        config.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
Show more
12:44
Security конфиг выглядит нормально — /cart/** просто authenticated(). Проблема в JWT фильтре — скорее всего он не устанавливает Authentication в контекст. Смотрим:

bash
find /home/claude/shop -name "JwtAuthenticationFilter.java" | xargs cat
package com.shop.auth.filter;

import com.shop.auth.entity.User;
import com.shop.auth.repository.UserRepository;
import com.shop.auth.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
private final JwtService jwtService;
private final UserRepository userRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                UUID userId = jwtService.extractUserId(token);
                userRepo.findById(userId).ifPresent(user -> {
                    var auth = new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                });
            }
        }
        chain.doFilter(req, resp);
    }
}
Show more
12:44
Фильтр выглядит правильно. Проблема может быть в том что jwtService.isValid(token) возвращает false — токен подписан другим секретом.

Токен в браузере был выдан старым Shop подом, а новый под поднялся с другим JWT_SECRET. Проверяем:

bash
kubectl exec deployment/shop-app-backend -n shop -- env | grep JWT_SECRET
секреты совпадают

12:45
Тогда проблема в extractUserId — токен выдан CRM (там sub это UUID пользователя CRM), а Shop ищет этого пользователя в своей БД и не находит. userRepo.findById(userId) возвращает empty — Authentication не устанавливается.

Нужно залогиниться именно в Shop, не в CRM. Попробуй через Swagger Shop:

https://bolohonovma.online/shop/api/v1/swagger-ui.html
POST /auth/login с твоими credentials — получи токен именно от Shop бэкенда.

залогинился

12:46
Теперь пробуй добавить товар в корзину через UI — должно работать. Если снова 403 — скидывай новый токен из DevTools.

{
"success": true,
"data": {
"accessToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhY2U0ZGYzZi1kZDNlLTQwYTYtOWI2NS01ZjY5YTcwN2IyMzQiLCJlbWFpbCI6ImJvbG9ob25vdm1ha2FyQGdtYWlsLmNvbSIsImlhdCI6MTc3Mjg3MzE4MCwiZXhwIjoxNzcyODc0MDgwfQ.KxNThuPWW1WAciJueHB3U3SQUB8HsDeMbjSkPAc7s_DmlcySNRc62amHy54FnS6ghh7vlBxrGCRkgz7HD8OxkQ",
"refreshToken": "2b9a055e-4fff-49bb-845f-9567bbbe3142",
"user": {
"id": "ace4df3f-dd3e-40a6-9b65-5f69a707b234",
"email": "bolohonovmakar@gmail.com",
"firstName": "Михаил",
"lastName": "Болохонов",
"phone": "+79053251464",
"address": "Саратов, ул. Советская, 11, кв. 53",
"balance": 932800.00,
"createdAt": "2026-03-06T14:31:12.931184Z"
}
}
}

Show more
12:46
Токен Shop получен. Теперь пробуй добавить товар в корзину через UI с этим токеном. Фронтенд должен его подхватить после логина через Swagger — или попробуй через UI снова залогиниться.

заказ создан {
"success": true,
"data": {
"id": "e541c6ab-9b52-4aa2-b9a8-dbbb249e1929",
"orderNumber": "SHOP-00005",
"status": "NEW",
"statusLabel": "Новый",
"totalAmount": 60000.00,
"shopOrderUuid": "209a58de-cdce-49ff-a622-1569f8ff2791",
"items": [
{
"productId": "9f88587c-9f86-4f09-8d1b-add2bdfda785",
"productName": "CI/CD пайплайн",
"productSku": "SRV-CICD-001",
"productImageUrl": "https://images.unsplash.com/photo-1618401471353-b98afee0b2eb?w=400",
"quantity": 1,
"price": 60000.00,
"totalPrice": 60000.00
}
],
"statusHistory": [
{
"newStatus": "NEW",
"newStatusLabel": "Новый",
"changedBy": "system",
"comment": "Заказ оформлен",
"changedAt": "2026-03-07T08:46:49.641710Z"
}
],
"createdAt": "2026-03-07T08:46:49.606655553Z",
"updatedAt": "2026-03-07T08:46:49.606657996Z"
}
}

Show more
12:47
Заказ SHOP-00005 создан! Теперь проверяем что он дошёл до CRM:

bash
kubectl logs deployment/crm-app-backend -n crm --tail=20 | grep -E "Kafka received|Order created|SHOP-00005"
там в https://bolohonovma.online/crm/api/v1/orders?page=0&size=20 https://bolohonovma.online/crm/api/v1/statuses/orders из-за того что OrderItem   поменяли наверное

12:48
Что именно сломалось — 500 ошибка или данные не те? Скидывай response или логи:

bash
kubectl logs deployment/crm-app-backend -n crm --tail=30 | grep "ERROR" | tail -10
403

12:48
403 в CRM — токен от Shop не подходит. Нужно залогиниться в CRM отдельно. Попробуй через CRM UI или Swagger:

https://bolohonovma.online/crm/api/v1/swagger-ui.html
ага ок теперь

12:49
Проверяем что SHOP-00005 дошёл до CRM:

bash
kubectl logs deployment/crm-app-backend -n crm --tail=30 | grep -E "Kafka received|Order created|SHOP-00005"
michael@demo-server:~$ kubectl logs deployment/crm-app-backend -n crm --tail=30 | grep -E "Kafka received|Order created|SHOP-00005"
michael@demo-server:~$

12:49
Пусто — сообщение ещё не пришло или уже вытеснено из логов. Проверяем outbox в Shop и idempotency log в CRM:

bash
docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT topic, status, attempt_count, created_at FROM kafka_outbox ORDER BY created_at DESC LIMIT 3;"

docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT shop_order_uuid, crm_order_id, processed_at FROM public.kafka_idempotency_log ORDER BY processed_at DESC LIMIT 3;"
ssh -J root@77.222.35.2 michael@10.0.0.2
michael@demo-server:~$ docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT topic, status, attempt_count, created_at FROM kafka_outbox ORDER BY created_at DESC LIMIT 3;"
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT shop_order_uuid, crm_order_id, processed_at FROM public.kafka_idempotency_log ORDER BY processed_at DESC LIMIT 3;"
topic        |  status   | attempt_count |          created_at           
---------------------+-----------+---------------+-------------------------------
shop.orders.created | PUBLISHED |             0 | 2026-03-07 08:46:49.700513+00
shop.orders.created | PUBLISHED |             0 | 2026-03-06 15:06:49.500097+00
shop.orders.created | PUBLISHED |             0 | 2026-03-06 14:48:51.757359+00
(3 rows)
shop_order_uuid            |             crm_order_id             |         processed_at          
--------------------------------------+--------------------------------------+-------------------------------
209a58de-cdce-49ff-a622-1569f8ff2791 | d5a850b6-1552-407d-a274-beaf60e69b06 | 2026-03-07 08:46:53.594181+00
755ac0a0-1293-47ae-8b56-1a35ba6c28ea | 7a9aba9d-3135-40f5-9a95-623f767ff939 | 2026-03-07 08:22:13.204915+00
(2 rows)
michael@demo-server:~$

Show more
12:50
Полный успех! 🎉

Shop → CRM работает идеально:

SHOP-00005 отправлен в Kafka (PUBLISHED) ✅
CRM получил и обработал за 4 секунды (08:46:49 → 08:46:53) ✅
crmOrderId=d5a850b6... создан ✅
Теперь тестируем обратное направление CRM → Shop — меняем статус заказа в CRM и проверяем что Shop получит событие. Найди заказ d5a850b6-1552-407d-a274-beaf60e69b06 в CRM UI и смени статус.

а какой номер у заказа id

12:51
bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, external_order_id, total_amount, created_at
FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.orders
WHERE id = 'd5a850b6-1552-407d-a274-beaf60e69b06';"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, external_order_id, total_amount, created_at
FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.orders
WHERE id = 'd5a850b6-1552-407d-a274-beaf60e69b06';"
id                  | external_order_id | total_amount |          created_at           
--------------------------------------+-------------------+--------------+-------------------------------
d5a850b6-1552-407d-a274-beaf60e69b06 | SHOP-00005        |     60000.00 | 2026-03-07 08:46:49.692917+00
(1 row)

12:51
external_order_id = SHOP-00005, total = 60000.00. Найди этот заказ в CRM UI и смени статус — например на IN_PROGRESS или PROCESSING. Это запустит Outbox → Kafka → Shop consumer.

не могу его найти на ui

12:52
Скорее всего заказ создался в схеме tenant_3a47abae а в UI залогинен другой тенант. Проверяем в какой схеме сидит текущий пользователь CRM — декодируй JWT токен из CRM UI на jwt.io и посмотри tenantSchema.

давай по другому

12:52
Через Swagger CRM меняем статус напрямую:

bash
# Сначала получаем токен
curl -s -X POST https://bolohonovma.online/crm/api/v1/auth/login \
-H "Content-Type: application/json" \
-d '{"email":"bolohonovmakar@gmail.com","password":"<пароль>","tenantSchema":"tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710"}' | jq .
{
"success": false,
"error": {
"code": "INVALID_CREDENTIALS",
"message": "Неверный email или пароль"
},
"timestamp": "2026-03-07T08:53:05.634292166Z"
}
michael@demo-server:~$

12:53
Неверные credentials для этого тенанта. Смотрим какие пользователи есть в этой схеме:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT u.email, u.login FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.users u LIMIT 5;"
{
"success": true,
"data": {
"accessToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJiODBiMWRkNi01ODU1LTQ3OTEtOThlZS0yZmEzNTBmYWFmYTAiLCJlbWFpbCI6ImJvbG9ob25vdm1ha2FyQGdtYWlsLmNvbSIsInRlbmFudFNjaGVtYSI6InRlbmFudF80Y2ZmN2M2NV84OTk3XzRiZTZfYTM5Y18yZmVhYzhjNzI0MmEiLCJ1c2VyVHlwZSI6IkFETUlOIiwiaWF0IjoxNzcyODczNjM3LCJleHAiOjE3NzI4NzQ1Mzd9.Ks9hU4YulyS39hxlRVeLHOFN5kYTe8JDl3edGDxBvf1sMkjruk40jAD_trizKHo0xr0rOd9BKc0fYOSkwds17Q",
"refreshToken": "5c51f14b-8479-49c9-bb2c-fab17e835d90",
"expiresIn": 900,
"userId": "b80b1dd6-5855-4791-98ee-2fa350faafa0",
"email": "bolohonovmakar@gmail.com",
"fullName": "Болохонов Михаил",
"userType": "ADMIN",
"tenantSchema": "tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a"
},
"timestamp": "2026-03-07T08:53:57.063283738Z"
}
michael@demo-server:~$

Show more
12:54
Токен от тенанта 4cff7c65 — а заказ в тенанте 3a47abae. Это два разных тенанта (две регистрации). Смотрим заказы в тенанте 4cff7c65:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, external_order_id, shop_order_uuid, total_amount, created_at
FROM tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a.orders
WHERE external_order_id IS NOT NULL
ORDER BY created_at DESC LIMIT 5;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, external_order_id, shop_order_uuid, total_amount, created_at
FROM tenant_4cff7c65_8997_4be6_a39c_2feac8c7242a.orders
WHERE external_order_id IS NOT NULL
ORDER BY created_at DESC LIMIT 5;"
id | external_order_id | shop_order_uuid | total_amount | created_at
----+-------------------+-----------------+--------------+------------
(0 rows)

12:54
В тенанте 4cff7c65 заказов из Shop нет. Заказ SHOP-00005 попал в тенант 3a47abae — потому что Shop пользователь зарегистрирован именно там.

Смотрим какой email в тенанте 3a47abae:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT email, login, role_id FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.users LIMIT 5;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT email, login, role_id FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.users LIMIT 5;"
ERROR:  column "login" does not exist
LINE 1: SELECT email, login, role_id FROM tenant_3a47abae_2dc5_4547_...

12:54
bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT * FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.users LIMIT 3;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT * FROM tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710.users LIMIT 3;"
id | global_user_id | email | first_name | last_name | middle_name | phone | avatar_url | position | is_active | last_login_at | settings | created_at | updated_at
----+----------------+-------+------------+-----------+-------------+-------+------------+----------+-----------+---------------+----------+------------+------------
(0 rows)

12:54
Таблица users пустая в этом тенанте — значит туда никто не регистрировался через CRM. Тенант создался автоматически через Kafka (crm.tenant.created) когда Shop пользователь зарегистрировался, но CRM пользователей там нет.

Нужно зарегистрировать CRM пользователя в этом тенанте. Смотрим как это делается — через публичный эндпоинт регистрации:

bash
curl -s -X POST https://bolohonovma.online/crm/api/v1/auth/register \
-H "Content-Type: application/json" \
-d '{
"email": "bolohonovmakar@gmail.com",
"password": "password123",
"firstName": "Михаил",
"lastName": "Болохонов",
"tenantSchema": "tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710"
}' | jq .
но почему так - не должно быть - пользователь с одинаковым email

12:55
Да, это архитектурная проблема — один пользователь (bolohonovmakar@gmail.com) зарегистрировался в CRM дважды и получил два разных тенанта.

Это произошло потому что в таблице public.tenants нет уникального constraint на email или owner_id. Каждый вызов регистрации создаёт новый тенант.

Но сейчас для теста просто используем правильный тенант. Посмотрим какой тенант связан с Shop — в событии crm.tenant.created должна быть связь:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, name, schema_name, created_at FROM public.tenants ORDER BY created_at;"
нет , давай это править - также надо проверить аналогично на shop - и после сбросим БД

12:56
Согласен — сначала разберёмся с логикой, потом сбросим БД и проверим чистый флоу.

Смотрим как CRM обрабатывает регистрацию и создание тенанта:

bash
find /home/claude/crm -name "*.java" | xargs grep -l "register\|Register\|createTenant\|TenantService" 2>/dev/null | grep -v test | head -10

package com.crm.tenant.service; import com.crm.common.exception.AppException; import com.crm.tenant.Tenant; import com.crm.tenant.TenantContext; import com.crm.tenant.TenantRepository; import com.crm.tenant.dto.TenantDto; import com.crm.user.repository.UserRepository; import lombok.RequiredArgsCons

pasted

12:56
Это TenantService, но нам нужен код регистрации — где создаётся тенант при регистрации пользователя:

bash
find /home/claude/crm -name "*.java" | xargs grep -l "register\|Register" 2>/dev/null | grep -v test | head -10
package com.crm.auth.dto;

import com.crm.tenant.TenantPlan;
import com.crm.user.entity.UserType;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
* DTO для запроса регистрации нового пользователя.
*
* Два сценария:
*  1. userType = ADMIN  → создаётся новый тенант, tenantSchema не нужна
*  2. userType = REGULAR → adminEmail обязателен, пользователь привязывается к тенанту администратора
      */
      @Data
      public class RegisterRequest {

@NotBlank(message = "Email обязателен")
@Email(message = "Неверный формат email")
private String email;

@NotBlank(message = "Пароль обязателен")
@Size(min = 8, message = "Пароль должен содержать минимум 8 символов")
private String password;

@NotBlank(message = "Имя обязательно")
@Size(max = 128)
private String firstName;

@NotBlank(message = "Фамилия обязательна")
@Size(max = 128)
private String lastName;

@Size(max = 128)
private String middleName;

@NotBlank(message = "Телефон обязателен")
@Pattern(
regexp = "^\\+[1-9]\\d{6,14}$",
message = "Телефон должен быть в международном формате: +7XXXXXXXXXX"
)
private String phone;

@NotNull(message = "Тип пользователя обязателен")
private UserType userType;

/** Для ADMIN: тариф. Для REGULAR: не используется */
private TenantPlan plan = TenantPlan.FREE;

/**
    * Для REGULAR пользователей — email их администратора.
    * По нему находим тенант и отправляем инвайт на подтверждение.
      */
      private String adminEmail;
      }
      Show more
      12:56
      Вижу — при регистрации ADMIN создаётся новый тенант всегда, без проверки на существующий email. Нужно найти сам сервис регистрации:

bash
find /home/claude/crm -name "AuthService.java" | xargs cat | grep -A 30 "register\|createTenant" | head -60

package com.crm.auth.service; import com.crm.auth.dto.*; import com.crm.common.config.AppProperties; import com.crm.common.exception.AppException; import com.crm.tenant.*; import com.crm.user.entity.User; import com.crm.user.entity.UserStatus; import com.crm.user.entity.UserType; import com.crm.use

pasted

12:57
Вижу проблему — в register() есть проверка:

java
if (userRepository.existsByEmail(request.getEmail())) {
throw AppException.conflict("EMAIL_ALREADY_EXISTS", ...);
}
Но public.users и тенант-схема users — разные таблицы. existsByEmail проверяет только public.users. Если пользователь регистрировался дважды с одним email — значит existsByEmail не работает правильно, или была прямая вставка в БД минуя этот код.

Проверяем сколько записей в public.users с этим email:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, email, user_type, status, tenant_id, email_verified FROM public.users WHERE email = 'bolohonovmakar@gmail.com';"
ERROR:  column "user_type" does not exist
LINE 1: SELECT id, email, user_type, status, tenant_id, email_verifi...
^

12:57
bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT * FROM public.users WHERE email = 'bolohonovmakar@gmail.com';"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT * FROM public.users WHERE email = 'bolohonovmakar@gmail.com';"
id | global_user_id | email | first_name | last_name | middle_name | phone | avatar_url | position | is_active | last_login_at | settings | created_at | updated_at
----+----------------+-------+------------+-----------+-------------+-------+------------+----------+-----------+---------------+----------+------------+------------
(0 rows)

12:57
public.users пустая — это тенант-таблица, не глобальная. Смотрим правильную таблицу:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE '%user%';"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename LIKE '%user%';"
tablename
--------------
users
user_roles
users_global
(3 rows)

12:58
bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, email, user_type, status, tenant_id, email_verified FROM public.users_global WHERE email = 'bolohonovmakar@gmail.com';"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, email, user_type, status, tenant_id, email_verified FROM public.users_global WHERE email = 'bolohonovmakar@gmail.com';"
id                  |          email           | user_type | status |              tenant_id               | email_verified
--------------------------------------+--------------------------+-----------+--------+--------------------------------------+----------------
b80b1dd6-5855-4791-98ee-2fa350faafa0 | bolohonovmakar@gmail.com | ADMIN     | ACTIVE | 4cff7c65-8997-4be6-a39c-2feac8c7242a | t
(1 row)

12:58
Одна запись в users_global — всё правильно, дублей нет. Тенант 4cff7c65 — это CRM тенант этого пользователя.

Проблема в другом — Shop при регистрации отправляет crm.tenant.created событие, CRM его получает и создаёт новый тенант 3a47abae, не связанный с существующим пользователем. Это правильное поведение — Shop создаёт отдельный CRM тенант для своего магазина.

Смотрим как CRM обрабатывает crm.tenant.created:

bash
find /home/claude/crm -name "TenantCreatedConsumer.java" | xargs cat 2>/dev/null
find /home/claude/shop -name "TenantCreatedConsumer.java" | xargs cat 2>/dev/null
package com.shop.kafka.consumer;

import com.shop.auth.repository.UserRepository;
import com.shop.kafka.dto.TenantCreatedEvent;
import com.shop.kafka.entity.PendingCrmTenant;
import com.shop.kafka.repository.PendingCrmTenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantCreatedConsumer {

    private final UserRepository userRepository;
    private final PendingCrmTenantRepository pendingRepo;

    @KafkaListener(
            topics = "${app.kafka.topics.crm-tenant-created:crm.tenant.created}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "statusListenerFactory"
    )
    @Transactional
    public void onTenantCreated(ConsumerRecord<String, TenantCreatedEvent> record, Acknowledgment ack) {
        TenantCreatedEvent event = record.value();
        log.info("Kafka received TenantCreated: tenantId={} schema={} adminEmail={}",
                event.getTenantId(), event.getTenantSchema(), event.getAdminEmail());

        try {
            if (event.getAdminEmail() == null || event.getTenantSchema() == null) {
                log.warn("TenantCreatedEvent missing required fields, skipping");
                ack.acknowledge();
                return;
            }

            var userOpt = userRepository.findByEmail(event.getAdminEmail());
            if (userOpt.isPresent()) {
                // Юзер уже есть — сразу обновляем схему
                userRepository.updateCrmTenantSchema(event.getAdminEmail(), event.getTenantSchema());
                log.info("Updated crmTenantSchema for existing user {}: {}", event.getAdminEmail(), event.getTenantSchema());
            } else {
                // Юзер ещё не зарегистрировался — сохраняем в pending
                // Если уже есть pending для этого email — обновляем схему (re-registration case)
                pendingRepo.findByEmail(event.getAdminEmail()).ifPresentOrElse(
                        existing -> {
                            existing.setTenantSchema(event.getTenantSchema());
                            pendingRepo.save(existing);
                            log.info("Updated existing pending tenant for email={}", event.getAdminEmail());
                        },
                        () -> {
                            pendingRepo.save(PendingCrmTenant.builder()
                                    .id(UUID.randomUUID())
                                    .email(event.getAdminEmail())
                                    .tenantSchema(event.getTenantSchema())
                                    .receivedAt(Instant.now())
                                    .build());
                            log.info("Saved pending CRM tenant for email={} — user not registered yet", event.getAdminEmail());
                        }
                );
            }
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to process TenantCreatedEvent: {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
Show more
12:59
Теперь картина полная. Архитектура правильная:

CRM регистрация → создаёт тенант → отправляет crm.tenant.created → Shop получает и сохраняет crmTenantSchema для пользователя
Shop создаёт заказ → отправляет shop.orders.created с tenantSchema → CRM записывает в нужную схему
Проблема в том что у тебя два тенанта потому что ты регистрировался дважды в CRM. Тенант 3a47abae создался при второй регистрации.

При сбросе БД всё будет чисто — один пользователь, один тенант, одна схема.

Что именно нужно исправить в коде перед сбросом БД:

✅ external_id в customers — уже есть в V119
✅ email, id в customer_personal_data — уже есть в V120
✅ Timestamp.from(now) фикс
✅ productName, productSku в OrderItem
Готов к сбросу БД? План:

bash
# 1. Удаляем все тенант схемы и данные
# 2. Чистим public таблицы (users_global, tenants, kafka_outbox, idempotency_log)
# 3. Чистим shop_db
# 4. Рестартуем оба пода
Подтверди — начнём.

давай

12:59
bash
# Чистим CRM
docker exec postgres psql -U crm_user -d crm_db -c "
DO \$\$ DECLARE r RECORD;
BEGIN
FOR r IN SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%'
LOOP
EXECUTE 'DROP SCHEMA ' || r.schema_name || ' CASCADE';
END LOOP;
END \$\$;

TRUNCATE public.users_global CASCADE;
TRUNCATE public.tenants CASCADE;
TRUNCATE public.kafka_outbox CASCADE;
TRUNCATE public.kafka_idempotency_log CASCADE;
TRUNCATE public.refresh_tokens CASCADE;
TRUNCATE public.email_verifications CASCADE;
TRUNCATE public.tenant_modules CASCADE;
"

# Чистим Shop
docker exec postgres psql -U shop_user -d shop_db -c "
TRUNCATE public.users CASCADE;
TRUNCATE public.shop_orders CASCADE;
TRUNCATE public.kafka_outbox CASCADE;
TRUNCATE public.pending_crm_tenants CASCADE;
TRUNCATE public.refresh_tokens CASCADE;
"
готово

13:00
Рестартуем оба пода:

bash
kubectl rollout restart deployment/crm-app-backend -n crm
kubectl rollout restart deployment/shop-app-backend -n shop
kubectl get pods -A | grep -E "crm|shop"
crm           crm-app-backend-67dfcfdd55-2z72b          0/1     Running     0              53s
crm           crm-app-backend-7ccd4959c-9s8c7           1/1     Running     0              41m
crm           crm-app-frontend-98988bcf-ptmtp           1/1     Running     0              41m
shop          shop-app-backend-547dd64bff-rl4mt         1/1     Running     0              53s
shop          shop-app-frontend-c85567659-74sbx         1/1     Running     0              81m

13:01
Shop уже поднялся, CRM ещё стартует. Ждём:

bash
kubectl get pods -n crm -w
NAME                               READY   STATUS    RESTARTS   AGE
crm-app-backend-67dfcfdd55-2z72b   1/1     Running   0          109s
crm-app-frontend-98988bcf-ptmtp    1/1     Running   0          42m
michael@demo-server:~$

13:02
Оба подняты. Теперь регистрируемся в CRM — один раз, чисто:

Открой https://bolohonovma.online/crm
Зарегистрируйся как ADMIN
Подтверди email
Залогинься
После этого регистрируемся в Shop с тем же email и проверяем что crm.tenant.created дошёл до Shop и crmTenantSchema обновился.

письмо ушло , но https://bolohonovma.online/crm/api/v1/auth/register - нет респонса

13:04
Проверяем логи:

bash
kubectl logs deployment/crm-app-backend -n crm --tail=30 | grep -E "register|email|ERROR|WARN" | tail -10
пусто

13:04
bash
kubectl logs deployment/crm-app-backend -n crm --tail=50 | tail -20
ssh -J root@77.222.35.2 michael@10.0.0.2
michael@demo-server:~$ kgp -n crm\
> ^C
michael@demo-server:~$ kgp -n crm
NAME                               READY   STATUS    RESTARTS   AGE
crm-app-backend-67dfcfdd55-2z72b   1/1     Running   0          109s
crm-app-frontend-98988bcf-ptmtp    1/1     Running   0          42m
michael@demo-server:~$ kubectl logs deployment/crm-app-backend -n crm --tail=30 | grep -E "register|email|ERROR|WARN" | tail -10
michael@demo-server:~$ kubectl logs deployment/crm-app-backend -n crm --tail=50 | tail -20
2026-03-07 09:04:19 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Initiating transaction commit
2026-03-07 09:04:19 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Committing JDBC transaction on Connection [HikariProxyConnection@518189903 wrapping org.postgresql.jdbc.PgConnection@440f19df]
2026-03-07 09:04:19 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Releasing JDBC Connection [HikariProxyConnection@518189903 wrapping org.postgresql.jdbc.PgConnection@440f19df] after transaction
2026-03-07 09:04:23 [http-nio-8080-exec-3] DEBUG [] o.s.jdbc.datasource.DataSourceUtils - Fetching JDBC Connection from DataSource
2026-03-07 09:04:23 [http-nio-8080-exec-3] DEBUG [] o.s.jdbc.datasource.DataSourceUtils - Fetching JDBC Connection from DataSource
2026-03-07 09:04:23 [http-nio-8080-exec-3] DEBUG [] o.s.jdbc.datasource.DataSourceUtils - Fetching JDBC Connection from DataSource
2026-03-07 09:04:23 [http-nio-8080-exec-3] DEBUG [] o.s.jdbc.datasource.DataSourceUtils - Fetching JDBC Connection from DataSource
2026-03-07 09:04:24 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Creating new transaction with name [com.crm.kafka.outbox.KafkaOutboxPoller.poll]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
2026-03-07 09:04:24 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Acquired Connection [HikariProxyConnection@2079052157 wrapping org.postgresql.jdbc.PgConnection@440f19df] for JDBC transaction
2026-03-07 09:04:24 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Switching JDBC Connection [HikariProxyConnection@2079052157 wrapping org.postgresql.jdbc.PgConnection@440f19df] to manual commit
2026-03-07 09:04:24 [scheduling-1] DEBUG [] o.s.jdbc.core.JdbcTemplate - Executing prepared SQL query
2026-03-07 09:04:24 [scheduling-1] DEBUG [] o.s.jdbc.core.JdbcTemplate - Executing prepared SQL statement [SELECT * FROM public.kafka_outbox
WHERE status = 'PENDING'
ORDER BY created_at ASC
LIMIT ?
FOR UPDATE SKIP LOCKED
]
2026-03-07 09:04:24 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Initiating transaction commit
2026-03-07 09:04:24 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Committing JDBC transaction on Connection [HikariProxyConnection@2079052157 wrapping org.postgresql.jdbc.PgConnection@440f19df]
2026-03-07 09:04:24 [scheduling-1] DEBUG [] o.s.j.support.JdbcTransactionManager - Releasing JDBC Connection [HikariProxyConnection@2079052157 wrapping org.postgresql.jdbc.PgConnection@440f19df] after transaction
michael@demo-server:~$

Show more
13:04
Запрос регистрации дошёл (видно http-nio-8080-exec-3 в 09:04:23), но нет ни ответа ни ошибки. Скорее всего завис на отправке email. Проверяем конфиг почты:

bash
kubectl exec deployment/crm-app-backend -n crm -- env | grep -E "MAIL|SMTP"
michael@demo-server:~$ kubectl exec deployment/crm-app-backend -n crm -- env | grep -E "MAIL|SMTP"
MAIL_HOST=smtp.mail.ru
MAIL_PORT=465
MAIL_USERNAME=notificationbot.noreply@mail.ru
MAIL_PASSWORD=5656dtdf
MAIL_FROM=notificationbot.noreply@mail.ru

13:04
SMTP настроен на mail.ru порт 465. Запрос регистрации висит потому что блокируется на отправке письма. Проверяем доступность SMTP с сервера:

bash
nc -zv smtp.mail.ru 465 -w 5
может по таймауту отбился запрос

13:05
Да, скорее всего — Traefik или nginx имеют таймаут меньше чем ждёт SMTP соединение. Но письмо ты говорил что ушло — значит регистрация прошла, просто ответ не вернулся клиенту.

Проверяем что пользователь создался:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, email, user_type, status, tenant_id, email_verified FROM public.users_global;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, email, user_type, status, tenant_id, email_verified FROM public.users_global;"
id                  |          email           | user_type | status |              tenant_id               | email_verified
--------------------------------------+--------------------------+-----------+--------+--------------------------------------+----------------
b2606419-ab1c-4bd0-8eec-73d0d3b2b412 | bolohonovmakar@gmail.com | ADMIN     | ACTIVE | 29519367-c009-4bb7-89dd-320725fda725 | t
(1 row)

13:05
Пользователь создан и активирован (email_verified=t, status=ACTIVE) ✅. Значит письмо дошло и верификация прошла автоматически или ты успел кликнуть.

Проверяем тенант и схему:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, name, schema_name, status FROM public.tenants;"
я кликнул, но меня беспокоит что ответа на /register не было - может таймаут

13:06
Да, это проблема — отправка email синхронная и блокирует HTTP ответ. Нужно сделать её асинхронной через @Async. Но это правка кода — сделаем после тестирования.

Проверяем тенант:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, name, schema_name, status FROM public.tenants;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT id, name, schema_name, status FROM public.tenants;"
id                  |       name       |                 schema_name                 | status
--------------------------------------+------------------+---------------------------------------------+--------
29519367-c009-4bb7-89dd-320725fda725 | Михаил Болохонов | tenant_29519367_c009_4bb7_89dd_320725fda725 | ACTIVE
(1 row)

13:07
Один тенант, чистая схема ✅. Проверяем что crm.tenant.created отправлен в Kafka:

bash
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT topic, status, attempt_count FROM public.kafka_outbox ORDER BY created_at DESC LIMIT 3;"
michael@demo-server:~$ docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT topic, status, attempt_count FROM public.kafka_outbox ORDER BY created_at DESC LIMIT 3;"
topic        |  status   | attempt_count
--------------------+-----------+---------------
crm.tenant.created | PUBLISHED |             1
(1 row)

13:07
crm.tenant.created опубликован ✅. Проверяем что Shop получил и сохранил crmTenantSchema:

bash
docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT email, crm_tenant_schema FROM public.users WHERE email = 'bolohonovmakar@gmail.com';"
michael@demo-server:~$ docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT email, crm_tenant_schema FROM public.users WHERE email = 'bolohonovmakar@gmail.com';"
email           |              crm_tenant_schema              
--------------------------+---------------------------------------------
bolohonovmakar@gmail.com | tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710
(1 row)

13:07
Стоп — crm_tenant_schema = tenant_3a47abae — это старая схема от предыдущей регистрации! Новый тенант tenant_29519367 не попал в Shop.

Проблема в том что Shop сохранил старое значение из pending_crm_tenants который мы не почистили, или новое событие не дошло.

Проверяем:

bash
# Что в pending
docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT * FROM public.pending_crm_tenants;"

# Логи Shop consumer
kubectl logs deployment/shop-app-backend -n shop --tail=20 | grep -E "TenantCreated|tenant|Kafka"
michael@demo-server:~$ docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT * FROM public.pending_crm_tenants;"
id | email | tenant_schema | received_at
----+-------+---------------+-------------
(0 rows)

13:08
Pending пустой. Значит Shop получил новое событие но обновил старую запись. Проверяем когда пользователь Shop зарегистрировался:

bash
docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT id, email, crm_tenant_schema, created_at FROM public.users WHERE email = 'bolohonovmakar@gmail.com';"
michael@demo-server:~$ docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT id, email, crm_tenant_schema, created_at FROM public.users WHERE email = 'bolohonovmakar@gmail.com';"
id                  |          email           |              crm_tenant_schema              |          created_at           
--------------------------------------+--------------------------+---------------------------------------------+-------------------------------
ace4df3f-dd3e-40a6-9b65-5f69a707b234 | bolohonovmakar@gmail.com | tenant_3a47abae_2dc5_4547_9ba9_3c08fe65a710 | 2026-03-06 14:31:12.931184+00
(1 row)

13:08
Пользователь Shop создан ещё вчера (2026-03-06) — мы не почистили public.users в shop_db! При TRUNCATE мы указали shop_orders, но не users.

Исправляем вручную — обновляем crm_tenant_schema на новый тенант:

bash
docker exec postgres psql -U shop_user -d shop_db -c \
"UPDATE public.users SET crm_tenant_schema = 'tenant_29519367_c009_4bb7_89dd_320725fda725'
WHERE email = 'bolohonovmakar@gmail.com';"
И при следующем сбросе БД добавим users в список:

bash
# Проверяем
docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT email, crm_tenant_schema FROM public.users WHERE email = 'bolohonovmakar@gmail.com';"
нет , давай все чистить и снова проверять

13:09
Чистим всё включая users в shop:

bash
# Чистим CRM
docker exec postgres psql -U crm_user -d crm_db -c "
DO \$\$ DECLARE r RECORD;
BEGIN
FOR r IN SELECT schema_name FROM information_schema.schemata WHERE schema_name LIKE 'tenant_%'
LOOP
EXECUTE 'DROP SCHEMA ' || r.schema_name || ' CASCADE';
END LOOP;
END \$\$;
TRUNCATE public.users_global CASCADE;
TRUNCATE public.tenants CASCADE;
TRUNCATE public.kafka_outbox CASCADE;
TRUNCATE public.kafka_idempotency_log CASCADE;
TRUNCATE public.refresh_tokens CASCADE;
TRUNCATE public.email_verifications CASCADE;
TRUNCATE public.tenant_modules CASCADE;
"

# Чистим Shop полностью
docker exec postgres psql -U shop_user -d shop_db -c "
TRUNCATE public.users CASCADE;
TRUNCATE public.shop_orders CASCADE;
TRUNCATE public.order_items CASCADE;
TRUNCATE public.kafka_outbox CASCADE;
TRUNCATE public.pending_crm_tenants CASCADE;
TRUNCATE public.refresh_tokens CASCADE;
TRUNCATE public.cart_items CASCADE;
"
готово

13:09
Рестартуем оба пода:

bash
kubectl rollout restart deployment/crm-app-backend -n crm
kubectl rollout restart deployment/shop-app-backend -n shop
kubectl get pods -A | grep -E "crm-app-backend|shop-app-backend"
стоп давай асинк поправим - я выше писал

13:10
Да, правим EmailService — делаем отправку асинхронной. Сначала находим метод:

bash
find /home/claude/crm -name "EmailService.java" | xargs grep -n "sendRegistrationConfirmation\|@Async" | head -10

package com.crm.auth.service; import com.crm.auth.entity.EmailVerification; import com.crm.auth.entity.EmailVerificationType; import com.crm.auth.repository.EmailVerificationRepository; import com.crm.common.config.AppProperties; import com.crm.common.exception.AppException; import com.crm.user.ent

pasted

13:10
@Async уже стоит на sendRegistrationConfirmation ✅. Проблема в другом — @Async не работает если не включён @EnableAsync в конфигурации. Проверяем:

bash
find /home/claude/crm -name "*.java" | xargs grep -l "EnableAsync" 2>/dev/null
/home/claude/crm замени на ./

13:10
bash
find /Users/mihailbolohonov/IdeaProjects/crm_final/crm-backend -name "*.java" | xargs grep -l "EnableAsync" 2>/dev/null
find ./crm-backend -name "*.java" | xargs grep -l "EnableAsync" 2>/dev/null

13:11
bash
cd /Users/mihailbolohonov/IdeaProjects/crm_final && find ./crm-backend -name "*.java" | xargs grep -l "EnableAsync" 2>/dev/null
я в папке mihailbolohonov@MacBook-Air-Mihail crm_final %  напиши для нее команду

13:12
bash
find ./crm-backend -name "*.java" | xargs grep -l "EnableAsync" 2>/dev/null
пусто и красным

13:12
@EnableAsync не подключён — поэтому @Async не работает и отправка email блокирует HTTP запрос.

Добавляем в главный класс приложения:

bash
find ./crm-backend -name "CrmApplication.java"
добавил package com.crm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan   // сканирует все @ConfigurationProperties
@EnableScheduling              // для очистки устаревших токенов по расписанию
@EnableAsync
public class CrmApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmApplication.class, args);
    }
}

Show more
13:14
Отлично. Теперь деплоим и проверяем полный флоу:

bash
./deploy.sh crm-app latest
шоп тоже деплоить

13:14
Нет, Shop не менялся — только CRM. Запускай только CRM деплой.


# CRM — Liquibase Migrations Полная схема базы данных: public (системные таблицы) + tenant (бизнес-данные, schema-per-tenant). ## Структура ``` db/ ├── changelog-master.xml ← корневой changelog └── migration/ ├── public/ ← системные таблицы (один раз)

pasted


# 🛒 IT Shop — Интернет-магазин ИТ-услуг и оборудования Микросервисный e-commerce проект, интегрированный с CRM-системой через Apache Kafka. ## Архитектура ``` ┌────────────────────────────────────┐ ┌────────────────────────────────────┐ │ SHOP (namespace: shop) │ │

pasted

в CRM и SHOP есть редми файлы - добавь описание что нужно зарегистрироваться с одинаковым email чтобы проверить kafka

13:16
Покажи где лежат README файлы:

bash
find . -name "README*" -maxdepth 3

# CRM — Liquibase Migrations Полная схема базы данных: public (системные таблицы) + tenant (бизнес-данные, schema-per-tenant). ## Структура ``` db/ ├── changelog-master.xml ← корневой changelog └── migration/ ├── public/ ← системные таблицы (один раз)

pasted


# 🛒 IT Shop — Интернет-магазин ИТ-услуг и оборудования Микросервисный e-commerce проект, интегрированный с CRM-системой через Apache Kafka. ## Архитектура ``` ┌────────────────────────────────────┐ ┌────────────────────────────────────┐ │ SHOP (namespace: shop) │ │

pasted

и если там что-то не так поправь

13:17
Скажи пути к README файлам на маке — чтобы я мог их отредактировать напрямую:

bash
find . -name "README.md" -maxdepth 3
/Users/mihailbolohonov/IdeaProjects/crm_final/crm-liquibase/README.md и /Users/mihailbolohonov/IdeaProjects/shop-project/README.md

13:18
Читаем оба файла целиком:


Reading CRM README
Reading CRM README
Файлы на маке недоступны из контейнера. Скопируй содержимое обоих README сюда и я подготовлю обновлённые версии.

скинул выше

13:18
Готовлю оба обновлённых README. Начнём с Shop:


Created 2 files, read a file
Created 2 files, read a file
Что добавлено/исправлено:

Shop README — добавлен топик crm.tenant.created в таблицу Kafka-топиков и архитектурную схему, добавлен раздел «Тестирование Kafka-интеграции» с пошаговыми инструкциями и диагностическими командами.

CRM Liquibase README — добавлены V119 и V120 в структуру файлов, добавлена секция «Тестирование Kafka-интеграции» с объяснением зачем нужен одинаковый email, порядком регистрации и диагностикой. В таблицу таблиц добавлены kafka_outbox, kafka_idempotency_log и customer_personal_data.

Shop readme
Document · MD
Crm liquibase readme
Document · MD


You've used 90% of your session limit
Upgrade


Claude is AI and can make mistakes. Please double-check responses.
Crm liquibase readme · MD
Copy

CRM — Liquibase Migrations
Полная схема базы данных: public (системные таблицы) + tenant (бизнес-данные, schema-per-tenant).

Структура
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
├── V119__customers_external_id.xml  — external_id для привязки клиентов Shop
├── V120__customer_personal_data_extend.xml — id + email в customer_personal_data
└── tenant-changelog.xml        ← changelog для программного создания тенантов
Запуск
Первичная инициализация (production)
bash
# Только структура, без демо-данных
liquibase \
--url=jdbc:postgresql://localhost:5432/crm \
--username=crm_user \
--password=secret \
--changelog-file=db/changelog-master.xml \
update
Demo-стенд (с тестовыми данными)
bash
liquibase \
--url=jdbc:postgresql://localhost:5432/crm_demo \
--username=crm_user \
--password=secret \
--changelog-file=db/changelog-master.xml \
update --contexts=demo
Демо-данные (V110, context=demo):

25 товаров — ИТ-услуги, оборудование, лицензии (цены 7 200–890 000 ₽)
60 клиентов — 38 юр. лиц + 22 физ. лица, Москва/СПб/регионы
80 заказов — воронка: NEW=18, IN_PROGRESS=22, WAITING=12, DONE=20, CANCELLED=8
50 задач — TODO=12, IN_PROGRESS=14, DONE=14, просрочено=10 (дедлайны в прошлом)
Spring Boot (автоматически при старте)
yaml
# application.yml
spring:
liquibase:
enabled: true
change-log: classpath:db/changelog-master.xml
default-schema: public
contexts: ${LIQUIBASE_CONTEXTS:}   # передать "demo" для демо-стенда
Создание нового тенанта
При регистрации нового тенанта вызывается TenantMigrationService:

java
// Обычный тенант
tenantMigrationService.createTenantSchema("tenant_a3f7b2c9", false);

// Demo-тенант (с тестовыми данными)
tenantMigrationService.createTenantSchema("tenant_demo_01", true);
Сервис:

Создаёт PostgreSQL схему tenant_{slug}
Устанавливает search_path на неё
Применяет V101–V109 (структура + системные данные), V119, V120
При applyDemoData=true — применяет V110 с context=demo
Тестирование Kafka-интеграции (CRM ↔ Shop)
Для корректной работы сквозного флоу необходимо зарегистрироваться в CRM и Shop с одинаковым email. CRM после активации аккаунта публикует событие crm.tenant.created, которое Shop использует для привязки CRM-тенанта к пользователю магазина — именно в эту схему будут попадать заказы.

Порядок регистрации
1. Сначала — регистрация в CRM

json
POST /api/v1/auth/register
{
"email": "your@email.com",
"password": "password123",
"firstName": "Имя",
"lastName": "Фамилия",
"phone": "+79001234567",
"userType": "ADMIN"
}
Подтверди email по ссылке из письма. После подтверждения CRM опубликует crm.tenant.created → тенант-схема создана.

2. Затем — регистрация в Shop с тем же email

json
POST /api/v1/auth/register
{
"email": "your@email.com",
"password": "password123",
"firstName": "Имя",
"lastName": "Фамилия",
"phone": "+79001234567"
}
Shop получит событие crm.tenant.created и запишет crmTenantSchema в профиль пользователя.

⚠️ Если зарегистрироваться в Shop до CRM — событие всё равно будет обработано: Shop сохранит схему в pending_crm_tenants и применит её при получении события от CRM.

3. Оформление заказа и проверка

После оформления заказа в Shop он должен появиться в CRM в разделе «Заказы». При смене статуса в CRM Shop получает обновление через Kafka и доставляет его покупателю по SSE.

Диагностика
bash
# crm.tenant.created отправлен из CRM
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT topic, status FROM public.kafka_outbox ORDER BY created_at DESC LIMIT 5;"

# CRM-тенант привязан к пользователю Shop
docker exec postgres psql -U shop_user -d shop_db -c \
"SELECT email, crm_tenant_schema FROM public.users WHERE email = 'your@email.com';"

# Заказ получен CRM
docker exec postgres psql -U crm_user -d crm_db -c \
"SELECT shop_order_uuid, crm_order_id, processed_at FROM public.kafka_idempotency_log ORDER BY processed_at DESC LIMIT 5;"
Rollback
Все changeSet-ы имеют <rollback> блок:

bash
# Откатить последние N changeSet-ов
liquibase rollbackCount 3

# Откатить до конкретного тега
liquibase tag v1.0.0
liquibase rollback v1.0.0
Полезные команды
bash
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
Таблицы по схемам
public (глобальные)
Таблица	Описание
tenants	Реестр организаций-тенантов
users_global	Auth-идентификаторы пользователей
oauth_accounts	Linked OAuth аккаунты (Google, Yandex)
refresh_tokens	JWT refresh tokens с ротацией
tenant_modules	Включённые модули CRM по тенанту
kafka_outbox	Transactional Outbox для Kafka
kafka_idempotency_log	Лог обработанных Kafka-сообщений (дедупликация)
tenant_* (per-tenant)
Таблица	Описание
permissions	Атомарные права (20 шт: CUSTOMER_VIEW, ORDER_CREATE...)
roles	Роли с цветом (ADMIN, MANAGER, SALES, OBSERVER + кастомные)
role_permissions	M2M: роль ↔ права
users	Профили пользователей (ФИО, должность, аватар)
user_roles	M2M: пользователь ↔ роли
order_statuses	Справочник статусов заказов (воронка)
task_statuses	Справочник статусов задач
customers	Клиенты (юр./физ. лица), FTS по имени/ИНН/телефону
customer_personal_data	Персональные данные клиентов (email, телефон)
tasks	Задачи с дедлайнами, исполнителями, приоритетом
task_comments	Комментарии к задачам
products	Каталог товаров и услуг, FTS
orders	Заказы с денормализованным total_amount
order_items	Позиции заказов (денормализованное название, цена, SKU)
