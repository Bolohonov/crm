package com.crm.order.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Позиция заказа.
 * price — цена на момент создания (не зависит от текущей цены товара)
 */
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("order_items")
public class OrderItem {

    @Id private UUID id;
    private UUID orderId;
    private UUID productId;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalPrice;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OrderItem oi)) return false;
        return Objects.equals(id, oi.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
