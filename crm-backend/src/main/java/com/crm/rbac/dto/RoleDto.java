package com.crm.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RoleDto {

    @Getter
    @Setter
    @EqualsAndHashCode
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

        private List<UUID> permissionIds;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class UpdateRequest {
        @NotBlank
        @Size(max = 128)
        private String name;

        @Size(max = 512)
        private String description;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class SetPermissionsRequest {
        private Set<UUID> permissionIds;
    }

    @Getter
    @Setter
    @EqualsAndHashCode
    public static class SetUserRolesRequest {
        private List<UUID> roleIds;
    }

    /** Ответ используется в RbacService через builder() */
    @Getter
    @Setter
    @EqualsAndHashCode
    @Builder
    public static class RoleResponse {
        private UUID   id;
        private String code;
        private String name;
        private String description;
        private boolean isSystem;
        private Instant createdAt;
        private List<PermissionDto.PermissionResponse> permissions;
    }
}
