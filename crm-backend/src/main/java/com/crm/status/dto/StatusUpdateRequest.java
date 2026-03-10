package com.crm.status.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import lombok.Getter;

@Getter
@Setter
public class StatusUpdateRequest {
    @NotBlank @Size(max = 100)
    private String name;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$")
    private String color;

    private int     sortOrder;
    private boolean isFinal;
}
