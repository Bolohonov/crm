package com.crm.product.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class ProductResponse {
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
