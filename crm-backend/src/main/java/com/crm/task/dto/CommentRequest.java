package com.crm.task.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
public class CommentRequest {
    @NotBlank
    @Size(max = 4096)
    private String content;
}
