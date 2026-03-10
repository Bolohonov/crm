package com.crm.order.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Заказ.
 * statusId       → словарь order_statuses (NEW, PICKING, SHIPPED, DELIVERED, ARCHIVED)
 * customerId     → клиент (обязателен)
 * authorId       → кто создал (null для заказов из магазина)
 * totalAmount    → хранится денормализованно для быстрых запросов,
 *                  пересчитывается при изменении позиций
 * externalOrderId → номер заказа в магазине (SHOP-00042), для отображения и трассировки
 * shopOrderUuid   → UUID заказа в магазине, используется как ключ Kafka-сообщений
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table("orders")
public class Order {
    @EqualsAndHashCode.Include
    @Id private UUID id;
    private UUID customerId;
    private UUID authorId;
    private UUID statusId;
    private String comment;
    private BigDecimal totalAmount;   // денормализованный итог
    /** Номер заказа в интернет-магазине (SHOP-00042). Null для заказов из CRM. */
    private String externalOrderId;
    /** UUID заказа в магазине — ключ для Kafka-событий. */
    private UUID shopOrderUuid;
    private Instant createdAt;
    private Instant updatedAt;
}
