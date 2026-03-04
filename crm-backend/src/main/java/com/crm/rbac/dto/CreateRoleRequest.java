package com.crm.rbac.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
public class CreateRoleRequest {
    @NotBlank @Pattern(regexp = "^[A-Z0-9_]+$") @Size(max = 64)
    private String code;
    @NotBlank @Size(max = 128)
    private String name;
    private String description;
    private List<UUID> permissionIds;
}
