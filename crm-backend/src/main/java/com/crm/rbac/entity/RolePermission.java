package com.crm.rbac.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/** Связь роль → право доступа */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("role_permissions")
public class RolePermission {
    private UUID roleId;
    private UUID permissionId;
}
