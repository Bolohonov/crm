package com.crm.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
public class ItemRequest {
    @NotNull(message = "Товар обязателен")
    private UUID productId;

    @NotNull
    @DecimalMin(value = "0.001", message = "Количество должно быть > 0")
    private BigDecimal quantity;

    /** Если null — используется текущая цена товара */
    private BigDecimal price;
}
