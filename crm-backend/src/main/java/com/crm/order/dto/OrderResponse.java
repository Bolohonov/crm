package com.crm.order.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class OrderResponse {
    private UUID id;

    private UUID customerId;
    private String customerName;

    private UUID authorId;
    private String authorName;

    private UUID statusId;
    private String statusName;
    private String statusCode;
    private String statusColor;

    private String comment;
    private BigDecimal totalAmount;
    private List<ItemResponse> items;

    /** Номер заказа в интернет-магазине (SHOP-00042). Null для ручных заказов. */
    private String externalOrderId;
    /** UUID заказа в магазине. Null для ручных заказов. */
    private UUID shopOrderUuid;
    /** Флаг: заказ пришёл из внешней системы (магазин). */
    private boolean fromShop;

    private Instant createdAt;
    private Instant updatedAt;
}
