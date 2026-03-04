package com.crm.dashboard.dto;

import java.math.BigDecimal;

public record RevenuePointDto(
    String     month,
    String     label,
    BigDecimal revenue,
    long       orderCount
) {}
