package com.crm.order.dto;

import jakarta.validation.Valid;
import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class OrderUpdateRequest {
    private UUID statusId;
    private String comment;
    @Valid private List<ItemRequest> items;
}
