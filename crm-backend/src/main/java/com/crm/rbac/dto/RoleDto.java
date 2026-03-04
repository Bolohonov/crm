package com.crm.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

public class RoleDto {

    @Data
    public static class CreateRequest {
        @NotBlank
        @Pattern(regexp = "^[A-Z0-9_]+$", message = "Код роли: только заглавные буквы, цифры и _")
        @Size(max = 64)
        private String code;

        @NotBlank
        @Size(max = 128)
        private String name;

        @Size(max = 512)
        private String description;
    }

    @Data
    public static class UpdateRequest {
        @NotBlank
        @Size(max = 128)
        private String name;

        @Size(max = 512)
        private String description;
    }

    @Data
    public static class SetPermissionsRequest {
        private Set<UUID> permissionIds;
    }

    @Data
    public static class SetUserRolesRequest {
        private Set<UUID> roleIds;
    }

    @Data
    public static class RoleResponse {
        private UUID id;
        private String code;
        private String name;
        private String description;
        private boolean isSystem;
        private java.util.List<PermissionDto.PermissionResponse> permissions;
    }
}
