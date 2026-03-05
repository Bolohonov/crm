package com.crm.product.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Товар/Услуга тенанта.
 * unit — единица измерения (шт, кг, час, ...) — из словаря PRODUCT_UNIT
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("products")
public class Product {
    @Id private UUID id;
    private String name;
    private String description;
    private String sku;           // артикул
    private BigDecimal price;     // базовая цена
    private String unit;          // единица измерения
    private UUID categoryId;      // категория (nullable)
    @Column("is_active")
    private boolean isActive;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
