package com.crm.order.dto;

import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class ItemResponse {
    private UUID id;
    private UUID productId;
    private String productName;
    private String productSku;
    private String productUnit;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal totalPrice;
}
