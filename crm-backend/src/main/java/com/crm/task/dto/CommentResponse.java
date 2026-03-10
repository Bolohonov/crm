package com.crm.task.dto;

import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class CommentResponse {
    private UUID id;
    private UUID authorId;
    private String authorName;
    private String content;
    private Instant createdAt;
    private Instant updatedAt;
}
