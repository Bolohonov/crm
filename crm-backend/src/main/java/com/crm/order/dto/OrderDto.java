package com.crm.order.dto;


import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class OrderDto {

    // ── Запросы ──────────────────────────────────────────────────────

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class CreateRequest {
        @NotNull(message = "Клиент обязателен")
        private UUID customerId;

        private UUID statusId;
        private String comment;

        @NotEmpty(message = "Заказ должен содержать хотя бы одну позицию")
        @Valid
        private List<ItemRequest> items;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class UpdateRequest {
        private UUID statusId;
        private String comment;
        @Valid private List<ItemRequest> items;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class ItemRequest {
        @NotNull(message = "Товар обязателен")
        private UUID productId;

        @NotNull
        @DecimalMin(value = "0.001", message = "Количество должно быть > 0")
        private BigDecimal quantity;

        /** Если null — используется текущая цена товара */
        private BigDecimal price;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class FilterRequest {
        private UUID customerId;
        private UUID statusId;
        private UUID authorId;
        private int page = 0;
        private int size = 20;
    }

    // ── Ответы ───────────────────────────────────────────────────────

    @Getter
    @Setter
    @EqualsAndHashCode @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OrderResponse {
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

    @Getter
    @Setter
    @EqualsAndHashCode @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ItemResponse {
        private UUID id;
        private UUID productId;
        private String productName;
        private String productSku;
        private String productUnit;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal totalPrice;
    }

    @Getter
    @Setter
    @EqualsAndHashCode @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PageResponse {
        private List<OrderResponse> content;
        private long totalElements;
        private int totalPages;
        private int page;
        private int size;
    }

    @Getter
    @Setter
    @EqualsAndHashCode @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StatsResponse {
        private long totalOrders;
        private BigDecimal totalRevenue;
        private long newOrders;
        private long completedOrders;
    }

    /** Смена статуса заказа */
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class ChangeStatusRequest {
        @NotNull
        private UUID   statusId;
        @Size(max = 1000)
        private String comment;
    }
}
