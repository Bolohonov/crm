package com.crm.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import lombok.Getter;

@Getter
@Setter
public class RoleUpdateRequest {
    @NotBlank
    @Size(max = 128)
    private String name;

    @Size(max = 512)
    private String description;
}
