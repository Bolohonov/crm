package com.crm.task.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class CalendarRequest {
    @NotNull
    private Instant from;
    @NotNull
    private Instant to;
    private UUID assigneeId;
}
