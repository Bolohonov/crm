package com.crm.dashboard.dto;

public record OverdueTaskDto(
    String id,
    String title,
    String priority,
    int    daysOverdue,
    String customerName,
    String assigneeName
) {}
