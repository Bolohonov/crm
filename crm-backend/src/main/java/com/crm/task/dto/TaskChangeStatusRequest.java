package com.crm.task.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class TaskChangeStatusRequest {
    @NotNull
    private UUID statusId;
    @Size(max = 1000)
    private String comment;
}