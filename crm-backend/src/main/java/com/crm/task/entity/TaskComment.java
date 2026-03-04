package com.crm.task.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("task_comments")
public class TaskComment {
    @Id private UUID id;
    private UUID taskId;
    private UUID authorId;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;
}
