package com.crm.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Входящее событие от интернет-магазина: «покупатель оформил заказ».
 *
 * Топик:     shop.orders.created
 * Источник:  магазин → CRM
 *
 * Пример сообщения:
 * <pre>
 * {
 *   "shopOrderId":   "SHOP-00042",
 *   "shopOrderUuid": "550e8400-e29b-41d4-a716-446655440000",
 *   "customer": {
 *     "externalId": "cust-123",
 *     "firstName":  "Иван",
 *     "lastName":   "Петров",
 *     "email":      "ivan@example.com",
 *     "phone":      "+79001234567"
 *   },
 *   "items": [
 *     { "sku": "LAPTOP-001", "name": "Ноутбук", "quantity": 1, "price": 75000.00 }
 *   ],
 *   "totalAmount": 75000.00,
 *   "comment":     "Позвонить перед доставкой",
 *   "createdAt":   "2026-03-01T10:00:00Z"
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ShopOrderCreatedEvent {

    /**
     * Человекочитаемый номер заказа в магазине (SHOP-00042).
     * Сохраняется в поле {@code external_order_id} заказа для трассировки.
     */
    private String shopOrderId;

    /**
     * UUID заказа на стороне магазина.
     * Используется как idempotency key — повторная публикация одного UUID
     * не создаёт дубликат заказа в CRM.
     */
    private UUID shopOrderUuid;
    /** Схема тенанта — в какой тенант писать заказ */
    private String tenantSchema;

    /** Информация о покупателе. */
    private CustomerInfo customer;

    /** Позиции заказа. */
    private List<ItemInfo> items;

    /**
     * Итоговая сумма из магазина.
     * CRM пересчитывает сумму сам по позициям — это поле используется
     * только для логирования расхождений.
     */
    private BigDecimal totalAmount;

    /** Произвольный комментарий от покупателя. */
    private String comment;

    /** Момент создания заказа в магазине. */
    private Instant createdAt;

    // ─────────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomerInfo {
        /** ID покупателя в системе магазина — для поиска существующего клиента в CRM. */
        private String externalId;
        private String firstName;
        private String lastName;
        private String middleName;
        private String email;
        private String phone;
        private String address;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ItemInfo {
        /**
         * SKU товара в магазине — ищем совпадение в таблице products.
         * Если SKU не найден — позиция создаётся с нулевым productId и
         * сохранённым именем товара.
         */
        private String sku;
        /** Название товара на момент заказа (денормализованное). */
        private String name;
        private BigDecimal quantity;
        /** Цена на момент заказа (из магазина). */
        private BigDecimal price;
    }
}
