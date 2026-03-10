package com.crm.rbac.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class RoleSetPermissionsRequest {
    private Set<UUID> permissionIds;
}
