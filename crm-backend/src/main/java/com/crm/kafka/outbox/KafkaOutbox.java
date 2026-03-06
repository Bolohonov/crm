package com.crm.kafka.outbox;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbox-запись для гарантированной доставки сообщений в Kafka.
 *
 * Паттерн Transactional Outbox:
 * 1. В рамках основной транзакции (changeStatus) сохраняем запись в outbox.
 * 2. Отдельный @Scheduled-поллер читает непубликованные записи и отправляет в Kafka.
 * 3. При успехе помечает запись как опубликованную.
 *
 * Это гарантирует, что сообщение будет отправлено даже если приложение упало
 * сразу после commit транзакции, но до отправки в Kafka.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("kafka_outbox")
public class KafkaOutbox implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = false;

    @Override
    public boolean isNew() { return isNew; }

    /** Название Kafka-топика. */
    private String topic;

    /**
     * Ключ Kafka-сообщения (используется для партиционирования).
     * Для заказов = shopOrderUuid или crmOrderId.
     */
    private String messageKey;

    /** JSON-payload сообщения. */
    private String payload;

    /** Статус: PENDING → PUBLISHED / FAILED. */
    private OutboxStatus status;

    /** Момент создания записи. */
    private Instant createdAt;

    /** Момент публикации в Kafka (null пока не опубликовано). */
    private Instant publishedAt;

    /** Количество попыток публикации. */
    private int attemptCount;

    /** Последняя ошибка (для диагностики). */
    private String lastError;

    public enum OutboxStatus {
        PENDING,    // ожидает публикации
        PUBLISHED,  // успешно отправлено в Kafka
        FAILED      // превышен лимит попыток
    }
}
