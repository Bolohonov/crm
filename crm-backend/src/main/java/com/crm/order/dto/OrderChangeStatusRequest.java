package com.crm.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.util.UUID;

/** Смена статуса заказа */
@Getter
@Setter
public class OrderChangeStatusRequest {
    @NotNull
    private UUID   statusId;
    @Size(max = 1000)
    private String comment;
}
