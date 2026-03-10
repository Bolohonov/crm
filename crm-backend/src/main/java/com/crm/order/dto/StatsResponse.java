package com.crm.order.dto;

import lombok.*;
import java.math.BigDecimal;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class StatsResponse {
    private long totalOrders;
    private BigDecimal totalRevenue;
    private long newOrders;
    private long completedOrders;
}
