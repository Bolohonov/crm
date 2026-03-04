package com.crm.kafka.producer;

import com.crm.kafka.config.KafkaProperties;
import com.crm.kafka.dto.OrderStatusChangedEvent;
import com.crm.kafka.outbox.KafkaOutbox;
import com.crm.kafka.outbox.KafkaOutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Producer уведомлений об изменении статуса заказа.
 *
 * НЕ отправляет напрямую в Kafka. Записывает в таблицу kafka_outbox
 * в рамках той же транзакции, что и changeStatus. Это гарантирует
 * атомарность: либо и статус изменён, и событие сохранено в outbox,
 * либо ничего.
 *
 * Фактическую отправку в Kafka выполняет {@link com.crm.kafka.outbox.KafkaOutboxPoller}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatusChangedProducer {

    private final KafkaOutboxRepository outboxRepository;
    private final KafkaProperties kafkaProperties;
    private final ObjectMapper objectMapper;

    /**
     * Сохраняет событие смены статуса в outbox.
     * Должен вызываться внутри @Transactional-метода OrderService.
     *
     * @param crmOrderId    UUID заказа в CRM
     * @param shopOrderId   Номер заказа в магазине (SHOP-00042), может быть null
     * @param shopOrderUuid UUID заказа в магазине, может быть null
     * @param previousStatus предыдущий статус (code)
     * @param newStatus      новый статус (code)
     * @param changedBy      кто изменил
     * @param comment        комментарий
     */
    public void enqueue(
        UUID crmOrderId,
        String shopOrderId,
        UUID shopOrderUuid,
        String previousStatus,
        String newStatus,
        String changedBy,
        String comment
    ) {
        // Не уведомляем магазин о переходе в NEW — он сам создал заказ
        if ("NEW".equals(newStatus)) {
            log.debug("Skipping outbox event for NEW status, crmOrderId={}", crmOrderId);
            return;
        }

        OrderStatusChangedEvent event = OrderStatusChangedEvent.builder()
            .crmOrderId(crmOrderId)
            .shopOrderId(shopOrderId)
            .shopOrderUuid(shopOrderUuid)
            .previousStatus(previousStatus)
            .newStatus(newStatus)
            .changedAt(Instant.now())
            .changedBy(changedBy != null ? changedBy : "system")
            .comment(comment)
            .build();

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // Теоретически невозможно для корректного объекта, но лучше упасть явно
            throw new IllegalStateException("Failed to serialize OrderStatusChangedEvent", e);
        }

        // Ключ = shopOrderUuid (или crmOrderId если нет uuid магазина)
        // Одинаковый ключ → одна партиция → гарантированный порядок событий для одного заказа
        String messageKey = shopOrderUuid != null
            ? shopOrderUuid.toString()
            : crmOrderId.toString();

        KafkaOutbox outbox = KafkaOutbox.builder()
            .id(UUID.randomUUID())
            .topic(kafkaProperties.getTopics().getCrmOrderStatusChanged())
            .messageKey(messageKey)
            .payload(payload)
            .status(KafkaOutbox.OutboxStatus.PENDING)
            .createdAt(Instant.now())
            .attemptCount(0)
            .build();

        outboxRepository.save(outbox);

        log.info("Outbox enqueued: crmOrderId={} {} → {} changedBy={}",
            crmOrderId, previousStatus, newStatus, changedBy);
    }
}
