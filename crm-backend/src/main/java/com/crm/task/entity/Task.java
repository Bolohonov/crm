package com.crm.task.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Задача тенанта.
 *
 * taskTypeId  → словарь TASK_TYPE  (звонок, встреча, письмо, ...)
 * statusId    → словарь TASK_STATUS (новая, в работе, выполнена, отменена)
 * assigneeId  → пользователь-исполнитель (nullable — не назначена)
 * customerId  → привязка к клиенту (nullable)
 * scheduledAt → запланированное время выполнения
 * completedAt → фактическое время завершения
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("tasks")
public class Task {
    @Id private UUID id;
    private String title;
    private String description;
    private UUID taskTypeId;
    private UUID statusId;
    private UUID authorId;
    private UUID assigneeId;
    private UUID customerId;
    private Instant scheduledAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
