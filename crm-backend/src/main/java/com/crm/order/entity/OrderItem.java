package com.crm.order.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Позиция заказа.
 * price — цена на момент создания (не зависит от текущей цены товара)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table("order_items")
public class OrderItem {
    @EqualsAndHashCode.Include
    @Id private UUID id;
    private UUID orderId;
    private UUID productId;
    private String productName;
    private String productSku;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalPrice;
}
