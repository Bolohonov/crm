package com.crm.product.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class ProductUpdateRequest {
    @Size(max = 512)  private String name;
    @Size(max = 4096) private String description;
    @Size(max = 128)  private String sku;
    @DecimalMin("0.0") private BigDecimal price;
    @Size(max = 32)   private String unit;
    private UUID categoryId;
    private Boolean isActive;
}
