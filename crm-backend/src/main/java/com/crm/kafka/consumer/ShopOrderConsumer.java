package com.crm.kafka.consumer;

import com.crm.kafka.config.KafkaProperties;
import com.crm.kafka.dto.ShopOrderCreatedEvent;
import com.crm.order.entity.Order;
import com.crm.order.entity.OrderItem;
import com.crm.order.repository.OrderItemRepository;
import com.crm.order.repository.OrderRepository;
import com.crm.product.repository.ProductRepository;
import com.crm.sse.SseNotificationService;
import com.crm.sse.SseOrderEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Kafka Consumer для входящих заказов из интернет-магазина.
 *
 * Алгоритм обработки сообщения:
 * 1. Проверяем идемпотентность (shopOrderUuid уже обработан?).
 * 2. Переключаем JdbcTemplate на схему тенанта магазина.
 * 3. Ищем или создаём клиента (Customer) по email / externalId.
 * 4. Создаём заказ (Order) со статусом NEW.
 * 5. Матчим позиции по SKU — если SKU не найден, фиксируем название товара.
 * 6. Записываем shopOrderUuid в idempotency_log.
 * 7. ACK — смещение фиксируется только после успешного commit транзакции.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShopOrderConsumer {

    private static final String NEW_STATUS_CODE = "NEW";

    private final OrderRepository     orderRepository;
    private final OrderItemRepository itemRepository;
    private final ProductRepository   productRepository;
    private final JdbcTemplate        jdbc;
    private final KafkaProperties     kafkaProps;
    private final SseNotificationService sseService;

    @KafkaListener(
            topics    = "${app.kafka.topics.shop-order-created}",
            groupId   = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void onShopOrderCreated(
            ConsumerRecord<String, ShopOrderCreatedEvent> record,
            Acknowledgment ack
    ) {
        ShopOrderCreatedEvent event = record.value();

        log.info("Kafka received: shopOrderId={} shopOrderUuid={} partition={} offset={}",
                event.getShopOrderId(), event.getShopOrderUuid(),
                record.partition(), record.offset());

        try {
            // Схема тенанта приходит в событии — магазин знает своего тенанта
            // Fallback на kafkaProps для обратной совместимости
            String schema = (event.getTenantSchema() != null && !event.getTenantSchema().isBlank())
                    ? event.getTenantSchema()
                    : kafkaProps.getShopTenantSchema();

            if (schema == null || schema.isBlank()) {
                log.error("Cannot determine tenant schema for shopOrderId={}", event.getShopOrderId());
                ack.acknowledge();
                return;
            }
            setSearchPath(schema);

            // 1. Идемпотентность — не обрабатываем дубликаты
            if (isAlreadyProcessed(event.getShopOrderUuid())) {
                log.warn("Duplicate shopOrderUuid={} — skipping", event.getShopOrderUuid());
                ack.acknowledge();
                return;
            }

            // 2. Находим или создаём клиента
            UUID customerId = resolveCustomer(event.getCustomer(), schema);

            // 3. Находим ID статуса NEW в схеме тенанта
            UUID newStatusId = resolveStatusId(NEW_STATUS_CODE);

            // 4. Формируем комментарий с номером заказа из магазина
            String comment = buildComment(event);

            // 5. Создаём заказ
            Order order = Order.builder()
                    .customerId(customerId)
                    .authorId(null)           // автор — система/магазин, не пользователь CRM
                    .statusId(newStatusId)
                    .externalOrderId(event.getShopOrderId())
                    .shopOrderUuid(event.getShopOrderUuid())
                    .comment(comment)
                    .totalAmount(BigDecimal.ZERO)
                    .createdAt(event.getCreatedAt() != null ? event.getCreatedAt() : Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            order = orderRepository.save(order);
            final UUID orderId = order.getId();

            // 6. Создаём позиции, матчим по SKU
            List<OrderItem> items = resolveItems(orderId, event.getItems(), schema);
            items.forEach(itemRepository::save);

            // 7. Пересчитываем и сохраняем итоговую сумму
            BigDecimal total = items.stream()
                    .map(OrderItem::getTotalPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            orderRepository.updateTotal(orderId, total);

            // Логируем расхождение с суммой из магазина
            if (event.getTotalAmount() != null
                    && total.compareTo(event.getTotalAmount()) != 0) {
                log.warn("Amount mismatch for shopOrderId={}: shop={} crm={}",
                        event.getShopOrderId(), event.getTotalAmount(), total);
            }

            // 8. Регистрируем как обработанное
            markProcessed(event.getShopOrderUuid(), orderId);

            log.info("Order created from shop: crmOrderId={} shopOrderId={} customerId={} items={} total={}",
                    orderId, event.getShopOrderId(), customerId, items.size(), total);

            // 9. SSE push — уведомляем всех подключённых клиентов тенанта
            String customerName = buildCustomerName(event.getCustomer());
            sseService.broadcast(
                    schema,
                    "order.created",
                    SseOrderEvent.orderCreated(orderId, event.getShopOrderId(), customerName, total)
            );

            ack.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process shopOrderId={} shopOrderUuid={}: {}",
                    event.getShopOrderId(), event.getShopOrderUuid(), ex.getMessage(), ex);
            // Не делаем ack — Spring Kafka повторит обработку согласно ErrorHandler (3 раза → DLQ)
            throw ex;
        }
    }

    // ── Идемпотентность ───────────────────────────────────────────────

    private boolean isAlreadyProcessed(UUID shopOrderUuid) {
        if (shopOrderUuid == null) return false;
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM public.kafka_idempotency_log WHERE shop_order_uuid = ?",
                    Integer.class, shopOrderUuid
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void markProcessed(UUID shopOrderUuid, UUID crmOrderId) {
        if (shopOrderUuid == null) return;
        jdbc.update(
                "INSERT INTO public.kafka_idempotency_log (shop_order_uuid, crm_order_id, processed_at) VALUES (?, ?, NOW())",
                shopOrderUuid, crmOrderId
        );
    }

    // ── Клиент ────────────────────────────────────────────────────────

    private UUID resolveCustomer(ShopOrderCreatedEvent.CustomerInfo info, String schema) {
        if (info == null) return createUnknownCustomer(schema);

        // Ищем по email
        if (info.getEmail() != null && !info.getEmail().isBlank()) {
            try {
                UUID existingId = jdbc.queryForObject(
                        "SELECT c.id FROM customers c " +
                                "JOIN customer_personal_data pd ON pd.customer_id = c.id " +
                                "WHERE pd.email = ? LIMIT 1",
                        UUID.class, info.getEmail().toLowerCase().trim()
                );
                if (existingId != null) {
                    log.debug("Found existing customer by email: {}", existingId);
                    return existingId;
                }
            } catch (Exception ignored) {}
        }

        // Ищем по external_id если заполнен
        if (info.getExternalId() != null && !info.getExternalId().isBlank()) {
            try {
                UUID existingId = jdbc.queryForObject(
                        "SELECT id FROM customers WHERE external_id = ? LIMIT 1",
                        UUID.class, info.getExternalId()
                );
                if (existingId != null) return existingId;
            } catch (Exception ignored) {}
        }

        // Создаём нового клиента
        return createCustomer(info);
    }

    private UUID createCustomer(ShopOrderCreatedEvent.CustomerInfo info) {
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbc.update(
                "INSERT INTO customers (id, type, status, external_id, created_at, updated_at) VALUES (?, 'INDIVIDUAL', 'ACTIVE', ?, ?, ?)",
                customerId, info.getExternalId(),
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now)
        );

        jdbc.update(
                "INSERT INTO customer_personal_data " +
                        "(id, customer_id, first_name, last_name, middle_name, email, phone, address) " +
                        "VALUES (uuid_generate_v4(), ?, ?, ?, ?, ?, ?, ?)",
                customerId,
                nullIfBlank(info.getFirstName()),
                nullIfBlank(info.getLastName()),
                nullIfBlank(info.getMiddleName()),
                info.getEmail() != null ? info.getEmail().toLowerCase().trim() : null,
                nullIfBlank(info.getPhone()),
                nullIfBlank(info.getAddress())
        );

        log.info("Created new customer from shop: id={} name={} {}",
                customerId, info.getLastName(), info.getFirstName());
        return customerId;
    }

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
                id,
                java.sql.Timestamp.from(now),
                java.sql.Timestamp.from(now)
        );
        jdbc.update(
                "INSERT INTO customer_personal_data " +
                        "(id, customer_id, first_name, last_name, email) " +
                        "VALUES (uuid_generate_v4(), ?, 'Покупатель', 'Из магазина', 'shop@noreply.local')",
                id
        );
        return id;
    }

    // ── Позиции заказа ────────────────────────────────────────────────

    private List<OrderItem> resolveItems(
            UUID orderId,
            List<ShopOrderCreatedEvent.ItemInfo> shopItems,
            String schema
    ) {
        List<OrderItem> result = new ArrayList<>();
        if (shopItems == null || shopItems.isEmpty()) return result;

        for (var item : shopItems) {
            UUID productId = null;
            BigDecimal price = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
            BigDecimal qty   = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;

            // Ищем товар по SKU
            if (item.getSku() != null && !item.getSku().isBlank()) {
                var productOpt = productRepository.findBySku(item.getSku());
                if (productOpt.isPresent()) {
                    productId = productOpt.get().getId();
                    // Цена из магазина имеет приоритет (зафиксирована на момент заказа)
                    if (item.getPrice() == null) {
                        price = productOpt.get().getPrice();
                    }
                } else {
                    log.warn("Product SKU not found in CRM: sku={} name={}", item.getSku(), item.getName());
                }
            }

            OrderItem orderItem = OrderItem.builder()
                    .orderId(orderId)
                    .productId(productId)
                    .productName(item.getName())
                    .productSku(item.getSku())
                    .quantity(qty)
                    .price(price)
                    .totalPrice(price.multiply(qty))
                    .build();

            result.add(orderItem);
        }
        return result;
    }

    // ── Вспомогательные методы ────────────────────────────────────────

    private UUID resolveStatusId(String code) {
        return jdbc.queryForObject(
                "SELECT id FROM order_statuses WHERE code = ?",
                UUID.class, code
        );
    }

    private String buildComment(ShopOrderCreatedEvent event) {
        StringBuilder sb = new StringBuilder();
        if (event.getShopOrderId() != null) {
            sb.append("Заказ из магазина: ").append(event.getShopOrderId());
        }
        if (event.getComment() != null && !event.getComment().isBlank()) {
            if (!sb.isEmpty()) sb.append(". ");
            sb.append(event.getComment());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private void setSearchPath(String schema) {
        jdbc.execute("SET search_path TO " + schema + ", public");
    }

    private String buildCustomerName(ShopOrderCreatedEvent.CustomerInfo info) {
        if (info == null) return "Покупатель из магазина";
        String last  = nullIfBlank(info.getLastName());
        String first = nullIfBlank(info.getFirstName());
        if (last == null && first == null) return info.getEmail() != null ? info.getEmail() : "Покупатель";
        if (first == null) return last;
        return last + " " + first.charAt(0) + ".";
    }

    private String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
