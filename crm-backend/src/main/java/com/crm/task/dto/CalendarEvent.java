package com.crm.task.dto;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

/** Облегчённый объект для календаря — без описания и комментариев */
@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class CalendarEvent {
    private UUID id;
    private String title;
    private Instant scheduledAt;
    private Instant completedAt;
    private String statusCode;
    private String statusColor;
    private String typeColor;
    private UUID assigneeId;
    private String assigneeName;
    private boolean overdue;
}
