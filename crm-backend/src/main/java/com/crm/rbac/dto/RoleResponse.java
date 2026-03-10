package com.crm.rbac.dto;

import lombok.Setter;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ответ используется в RbacService через builder() */
@Getter
@Setter
@Builder
public class RoleResponse {
    private UUID   id;
    private String code;
    private String name;
    private String description;
    private boolean isSystem;
    private Instant createdAt;
    private List<PermissionResponse> permissions;
}
