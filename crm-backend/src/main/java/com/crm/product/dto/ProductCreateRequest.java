package com.crm.product.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class ProductCreateRequest {
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
