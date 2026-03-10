package com.crm.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class OrderCreateRequest {
    @NotNull(message = "Клиент обязателен")
    private UUID customerId;

    private UUID statusId;
    private String comment;

    @NotEmpty(message = "Заказ должен содержать хотя бы одну позицию")
    @Valid
    private List<ItemRequest> items;
}
