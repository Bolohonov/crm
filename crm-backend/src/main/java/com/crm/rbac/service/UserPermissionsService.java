package com.crm.rbac.service;

import com.crm.rbac.entity.Role;
import com.crm.rbac.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionsService {

    private final JdbcTemplate jdbc;
    private final RoleRepository roleRepository;

    @Cacheable(
            cacheNames = "userPermissions",
            key = "#tenantSchema + '::' + #userId"
    )
    public Set<String> getPermissionCodes(UUID userId, String tenantSchema) {
        log.debug("Loading permissions from DB for user: {}, schema: {}", userId, tenantSchema);

        // Явно указываем схему в SQL — search_path здесь ненадёжен
        List<String> codes = jdbc.queryForList(
                """
                SELECT DISTINCT p.code
                FROM "%s".permissions p
                INNER JOIN "%s".role_permissions rp ON rp.permission_id = p.id
                INNER JOIN "%s".user_roles ur ON ur.role_id = rp.role_id
                WHERE ur.user_id = ?
                """.formatted(tenantSchema, tenantSchema, tenantSchema),
                String.class,
                userId
        );

        Set<String> result = new HashSet<>(codes);
        log.debug("Loaded {} permissions for user: {}", result.size(), userId);
        return result;
    }

    @Cacheable(
            cacheNames = "userPermissions",
            key = "'roles::' + #tenantSchema + '::' + #userId"
    )
    public List<Role> getUserRoles(UUID userId, String tenantSchema) {
        return roleRepository.findRolesByUserId(userId);
    }

    public boolean hasPermission(UUID userId, String tenantSchema, String permissionCode) {
        return getPermissionCodes(userId, tenantSchema).contains(permissionCode);
    }

    @CacheEvict(
            cacheNames = "userPermissions",
            key = "#tenantSchema + '::' + #userId"
    )
    public void evictUserPermissions(UUID userId, String tenantSchema) {
        log.debug("Permissions cache evicted for user: {}, schema: {}", userId, tenantSchema);
    }

    @CacheEvict(
            cacheNames = "userPermissions",
            key = "'roles::' + #tenantSchema + '::' + #userId"
    )
    public void evictUserRoles(UUID userId, String tenantSchema) {
        log.debug("Roles cache evicted for user: {}", userId);
    }

    public void evictAll(UUID userId, String tenantSchema) {
        evictUserPermissions(userId, tenantSchema);
        evictUserRoles(userId, tenantSchema);
    }
}
