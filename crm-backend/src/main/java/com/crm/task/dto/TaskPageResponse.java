package com.crm.task.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class TaskPageResponse {
    private List<TaskResponse> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
}
