package com.crm.kafka.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Исходящее событие от CRM: «статус заказа изменён».
 *
 * Топик:     crm.orders.status_changed
 * Источник:  CRM → магазин
 *
 * Публикуется при каждом изменении статуса кроме перехода в NEW
 * (NEW создаётся самим магазином, поэтому уведомление излишне).
 *
 * Ключ Kafka-сообщения = shopOrderUuid.toString() — гарантирует порядок
 * событий для одного заказа внутри одной партиции.
 *
 * Жизненный цикл статусов (CRM → магазин):
 *   PICKING   — заказ принят, началась комплектация
 *   SHIPPED   — заказ передан в доставку
 *   DELIVERED — заказ получен покупателем
 *   ARCHIVED  — заказ закрыт и переведён в архив
 *
 * Пример JSON:
 * <pre>
 * {
 *   "crmOrderId":    "a1b2c3d4-...",
 *   "shopOrderId":   "SHOP-00042",
 *   "shopOrderUuid": "550e8400-...",
 *   "previousStatus": "PICKING",
 *   "newStatus":      "SHIPPED",
 *   "changedAt":      "2026-03-01T14:30:00Z",
 *   "changedBy":      "Иванов И.",
 *   "comment":        "Отправлено СДЭК, трек: 12345678"
 * }
 * </pre>
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderStatusChangedEvent {

    /** UUID заказа в CRM. */
    private UUID crmOrderId;

    /** Человекочитаемый номер заказа в магазине (SHOP-00042). */
    private String shopOrderId;

    /**
     * UUID заказа в магазине — основной ключ для идентификации на стороне магазина.
     * Null только для заказов, созданных вручную в CRM (не из магазина).
     */
    private UUID shopOrderUuid;

    /** Предыдущий статус (enum-код: NEW, PICKING, SHIPPED, DELIVERED, ARCHIVED). */
    private String previousStatus;

    /** Новый статус. */
    private String newStatus;

    /** Когда произошло изменение. */
    private Instant changedAt;

    /** Кто изменил: «Фамилия И.» или «system» для автоматических переходов. */
    private String changedBy;

    /** Произвольный комментарий при смене статуса (трек-номер и т.п.). */
    private String comment;
}
