package com.crm.dashboard.dto;

import java.math.BigDecimal;

public record FunnelStageDto(
    String     statusCode,
    String     statusName,
    String     color,
    long       orderCount,
    BigDecimal totalAmount,
    int        pct,
    int        conversionPct
) {}
