package com.crm.dashboard.dto;

import java.math.BigDecimal;

public record TopCustomerDto(
    String     customerId,
    String     customerName,
    String     customerType,
    long       orderCount,
    BigDecimal totalRevenue
) {}
