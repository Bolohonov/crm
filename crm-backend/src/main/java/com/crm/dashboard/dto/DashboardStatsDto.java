package com.crm.dashboard.dto;

import java.math.BigDecimal;

public record DashboardStatsDto(
    CustomerStats customers,
    OrderStats    orders,
    TaskStats     tasks,
    ProductStats  products
) {
    public record CustomerStats(
        long total,
        long newThisMonth,
        int  growthPct
    ) {}

    public record OrderStats(
        long       total,
        BigDecimal totalRevenue,
        BigDecimal revenueThisMonth,
        int        revenueGrowthPct,
        BigDecimal avgOrderAmount
    ) {}

    public record TaskStats(
        long total,
        long overdue,
        long dueToday,
        long completedThisWeek
    ) {}

    public record ProductStats(
        long total,
        long active
    ) {}
}
