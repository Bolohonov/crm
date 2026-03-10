package com.crm.task.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class TaskCreateRequest {
    @NotBlank(message = "Название задачи обязательно")
    @Size(max = 512)
    private String title;

    @Size(max = 4096)
    private String description;

    @NotNull(message = "Тип задачи обязателен")
    private UUID taskTypeId;

    @NotNull(message = "Статус задачи обязателен")
    private UUID statusId;

    private UUID assigneeId;
    private UUID customerId;
    private Instant scheduledAt;
}
