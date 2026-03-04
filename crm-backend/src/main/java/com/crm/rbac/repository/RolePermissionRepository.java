package com.crm.rbac.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Репозиторий для таблицы role_permissions (составной PK без суррогатного id).
 * Spring Data JDBC не поддерживает составной PK напрямую —
 * используем JdbcTemplate с нативными запросами.
 * search_path уже выставлен через TenantContext.
 */
@Repository
@RequiredArgsConstructor
public class RolePermissionRepository {

    private final JdbcTemplate jdbcTemplate;

    public void addPermissionToRole(UUID roleId, UUID permissionId) {
        jdbcTemplate.update("""
            INSERT INTO role_permissions (role_id, permission_id)
            VALUES (?, ?)
            ON CONFLICT (role_id, permission_id) DO NOTHING
            """,
            roleId, permissionId
        );
    }

    public void removePermissionFromRole(UUID roleId, UUID permissionId) {
        jdbcTemplate.update(
            "DELETE FROM role_permissions WHERE role_id = ? AND permission_id = ?",
            roleId, permissionId
        );
    }

    public void removeAllPermissionsFromRole(UUID roleId) {
        jdbcTemplate.update(
            "DELETE FROM role_permissions WHERE role_id = ?",
            roleId
        );
    }

    public boolean exists(UUID roleId, UUID permissionId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM role_permissions WHERE role_id = ? AND permission_id = ?",
            Integer.class, roleId, permissionId
        );
        return count != null && count > 0;
    }
}
