package com.crm.rbac.dto;

import lombok.Setter;
import lombok.Getter;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class SetPermissionsRequest {
    private Set<UUID> permissionIds;
}
