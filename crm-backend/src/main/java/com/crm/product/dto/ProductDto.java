package com.crm.product.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class ProductDto {

    @Data
    public static class CreateRequest {
        @NotBlank(message = "Название обязательно")
        @Size(max = 512)
        private String name;

        @Size(max = 4096)
        private String description;

        @Size(max = 128)
        private String sku;

        @NotNull(message = "Цена обязательна")
        @DecimalMin(value = "0.0", message = "Цена не может быть отрицательной")
        private BigDecimal price;

        @Size(max = 32)
        private String unit = "шт";

        private UUID categoryId;
        private boolean isActive = true;
    }

    @Data
    public static class UpdateRequest {
        @Size(max = 512)  private String name;
        @Size(max = 4096) private String description;
        @Size(max = 128)  private String sku;
        @DecimalMin("0.0") private BigDecimal price;
        @Size(max = 32)   private String unit;
        private UUID categoryId;
        private Boolean isActive;
    }

    @Data
    public static class SearchRequest {
        private String query;
        private UUID categoryId;
        private boolean onlyActive = true;
        private int page = 0;
        private int size = 20;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ProductResponse {
        private UUID id;
        private String name;
        private String description;
        private String sku;
        private BigDecimal price;
        private String unit;
        private UUID categoryId;
        private String categoryName;
        private boolean isActive;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PageResponse {
        private List<ProductResponse> content;
        private long totalElements;
        private int totalPages;
        private int page;
        private int size;
    }
}
