package com.crm.task.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
public class TaskFilterRequest {
    private UUID assigneeId;
    private UUID statusId;
    private UUID typeId;
    private UUID customerId;
    private int page = 0;
    private int size = 20;
}
