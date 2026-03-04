package com.crm.sse;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Payload SSE-события связанного с заказом.
 *
 * Используется для двух типов событий:
 *   - "order.created"         — новый заказ пришёл из магазина
 *   - "order.status_changed"  — статус заказа изменён
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseOrderEvent {

    /** Тип события */
    private String type;

    /** ID заказа в CRM */
    private UUID orderId;

    /** Номер заказа в магазине (если пришёл через Kafka) */
    private String externalOrderId;

    /** Имя клиента */
    private String customerName;

    /** Сумма заказа */
    private BigDecimal totalAmount;

    /** Новый статус (код) */
    private String newStatus;

    /** Предыдущий статус (для order.status_changed) */
    private String previousStatus;

    /** Метка времени события */
    private Instant occurredAt;

    // ── Фабричные методы ──────────────────────────────────────────────

    public static SseOrderEvent orderCreated(UUID orderId, String externalOrderId,
                                              String customerName, BigDecimal totalAmount) {
        return SseOrderEvent.builder()
            .type("order.created")
            .orderId(orderId)
            .externalOrderId(externalOrderId)
            .customerName(customerName)
            .totalAmount(totalAmount)
            .occurredAt(Instant.now())
            .build();
    }

    public static SseOrderEvent statusChanged(UUID orderId, String externalOrderId,
                                               String previousStatus, String newStatus) {
        return SseOrderEvent.builder()
            .type("order.status_changed")
            .orderId(orderId)
            .externalOrderId(externalOrderId)
            .previousStatus(previousStatus)
            .newStatus(newStatus)
            .occurredAt(Instant.now())
            .build();
    }
}
