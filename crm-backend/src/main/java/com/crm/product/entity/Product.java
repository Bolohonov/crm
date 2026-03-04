package com.crm.product.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Товар/Услуга тенанта.
 * unit — единица измерения (шт, кг, час, ...) — из словаря PRODUCT_UNIT
 */
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("products")
public class Product {

    @Id private UUID id;
    private String name;
    private String description;
    private String sku;
    private BigDecimal price;
    private String unit;
    private UUID categoryId;
    private boolean isActive;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Product p)) return false;
        return Objects.equals(id, p.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
