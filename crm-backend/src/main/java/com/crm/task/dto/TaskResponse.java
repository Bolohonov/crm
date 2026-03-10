package com.crm.task.dto;

import lombok.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class TaskResponse {
    private UUID id;
    private String title;
    private String description;

    // Обогащённые данные из словарей
    private UUID taskTypeId;
    private String taskTypeName;
    private String taskTypeColor;

    private UUID statusId;
    private String statusName;
    private String statusCode;
    private String statusColor;

    // Участники
    private UUID authorId;
    private String authorName;
    private UUID assigneeId;
    private String assigneeName;

    // Клиент
    private UUID customerId;
    private String customerName;

    // Время
    private Instant scheduledAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;

    // Флаги
    private boolean overdue;
    private boolean dueToday;

    // Комментарии
    private List<CommentResponse> comments;
}
