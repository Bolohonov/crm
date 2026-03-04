package com.crm.kafka.outbox;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Outbox-запись для гарантированной доставки сообщений в Kafka.
 *
 * Паттерн Transactional Outbox:
 * 1. В рамках основной транзакции (changeStatus) сохраняем запись в outbox.
 * 2. Отдельный @Scheduled-поллер читает непубликованные записи и отправляет в Kafka.
 * 3. При успехе помечает запись как опубликованную.
 */
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("kafka_outbox")
public class KafkaOutbox {

    @Id
    private UUID id;

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

    private Instant createdAt;
    private Instant publishedAt;
    private int attemptCount;
    private String lastError;

    public enum OutboxStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KafkaOutbox ko)) return false;
        return Objects.equals(id, ko.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
