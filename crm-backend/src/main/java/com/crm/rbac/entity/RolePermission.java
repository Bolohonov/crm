package com.crm.rbac.entity;

import lombok.*;
import org.springframework.data.relational.core.mapping.Table;

import java.util.Objects;
import java.util.UUID;

/** Связь роль → право доступа */
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("role_permissions")
public class RolePermission {

    private UUID roleId;
    private UUID permissionId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermission rp)) return false;
        return Objects.equals(roleId, rp.roleId)
            && Objects.equals(permissionId, rp.permissionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, permissionId);
    }
}
