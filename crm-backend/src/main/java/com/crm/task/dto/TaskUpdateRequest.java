package com.crm.task.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class TaskUpdateRequest {
    @Size(max = 512)
    private String title;

    @Size(max = 4096)
    private String description;

    private UUID taskTypeId;
    private UUID statusId;
    private UUID assigneeId;
    private UUID customerId;
    private Instant scheduledAt;
}
