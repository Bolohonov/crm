package com.crm.rbac.repository;

import com.crm.rbac.entity.Permission;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface PermissionRepository extends CrudRepository<Permission, UUID> {

    List<Permission> findAll();

    List<Permission> findByModule(String module);

    /**
     * Все пермиссии пользователя через цепочку user_roles → role_permissions → permissions.
     */
    @Query("""
        SELECT DISTINCT p.*
        FROM permissions p
        INNER JOIN role_permissions rp ON rp.permission_id = p.id
        INNER JOIN user_roles ur ON ur.role_id = rp.role_id
        WHERE ur.user_id = :userId
        """)
    Set<Permission> findPermissionsByUserId(UUID userId);

    /**
     * Только коды пермиссий — быстрее, без лишних полей.
     * Используется для JWT claims и проверок в @PreAuthorize.
     */
    @Query("""
        SELECT DISTINCT p.code
        FROM permissions p
        INNER JOIN role_permissions rp ON rp.permission_id = p.id
        INNER JOIN user_roles ur ON ur.role_id = rp.role_id
        WHERE ur.user_id = :userId
        """)
    Set<String> findPermissionCodesByUserId(UUID userId);

    /**
     * Пермиссии конкретной роли.
     */
    @Query("""
        SELECT p.*
        FROM permissions p
        INNER JOIN role_permissions rp ON rp.permission_id = p.id
        WHERE rp.role_id = :roleId
        """)
    List<Permission> findByRoleId(UUID roleId);
}
