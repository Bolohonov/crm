package com.crm.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import lombok.Getter;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class RoleCreateRequest {
    @NotBlank
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Код роли: только заглавные буквы, цифры и _")
    @Size(max = 64)
    private String code;

    @NotBlank
    @Size(max = 128)
    private String name;

    @Size(max = 512)
    private String description;

    private List<UUID> permissionIds;
}
