package com.crm.task.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table("task_comments")
public class TaskComment {
    @EqualsAndHashCode.Include
    @Id private UUID id;
    private UUID taskId;
    private UUID authorId;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;
}
