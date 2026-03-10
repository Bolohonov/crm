package com.crm.rbac.entity;

import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

import java.util.UUID;

/** Связь роль → право доступа */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table("role_permissions")
public class RolePermission {
    private UUID roleId;
    private UUID permissionId;
}
