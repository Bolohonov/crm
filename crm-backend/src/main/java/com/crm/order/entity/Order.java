package com.crm.order.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Заказ.
 * statusId       → словарь order_statuses (NEW, PICKING, SHIPPED, DELIVERED, ARCHIVED)
 * customerId     → клиент (обязателен)
 * authorId       → кто создал (null для заказов из магазина)
 * totalAmount    → хранится денормализованно для быстрых запросов
 * externalOrderId → номер заказа в магазине (SHOP-00042)
 * shopOrderUuid   → UUID заказа в магазине, используется как ключ Kafka-сообщений
 */
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("orders")
public class Order {

    @Id private UUID id;
    private UUID customerId;
    private UUID authorId;
    private UUID statusId;
    private String comment;
    private BigDecimal totalAmount;
    private String externalOrderId;
    private UUID shopOrderUuid;
    private Instant createdAt;
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order ord)) return false;
        return Objects.equals(id, ord.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
