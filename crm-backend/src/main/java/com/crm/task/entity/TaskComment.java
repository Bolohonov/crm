package com.crm.task.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("task_comments")
public class TaskComment {

    @Id private UUID id;
    private UUID taskId;
    private UUID authorId;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskComment tc)) return false;
        return Objects.equals(id, tc.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
