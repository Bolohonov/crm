package com.crm.rbac.service;

import com.crm.common.exception.AppException;
import com.crm.rbac.config.Permissions;
import com.crm.rbac.entity.Permission;
import com.crm.rbac.entity.Role;
import com.crm.rbac.entity.UserRole;
import com.crm.rbac.repository.PermissionRepository;
import com.crm.rbac.repository.RolePermissionRepository;
import com.crm.rbac.repository.RoleRepository;
import com.crm.rbac.repository.UserRoleRepository;
import com.crm.tenant.TenantContext;
import com.crm.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Управление ролями и пермиссиями тенанта.
 * Все операции изменения сбрасывают Redis-кэш затронутых пользователей.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionsService permissionsService;

    // ----------------------------------------------------------------
    //  Роли
    // ----------------------------------------------------------------

    @PreAuthorize("@sec.isAdmin()")
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @PreAuthorize("@sec.has('" + Permissions.ROLE_MANAGE + "')")
    @Transactional
    public Role createRole(String code, String name, String description) {
        if (roleRepository.existsByCode(code)) {
            throw AppException.conflict("ROLE_CODE_EXISTS",
                "Роль с кодом '" + code + "' уже существует");
        }
        Role role = Role.builder()
            .code(code.toUpperCase())
            .name(name)
            .description(description)
            .isSystem(false)
            .createdAt(Instant.now())
            .build();
        return roleRepository.save(role);
    }

    @PreAuthorize("@sec.has('" + Permissions.ROLE_MANAGE + "')")
    @Transactional
    public Role updateRole(UUID roleId, String name, String description) {
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> AppException.notFound("Роль"));
        role.setName(name);
        role.setDescription(description);
        return roleRepository.save(role);
    }

    @PreAuthorize("@sec.has('" + Permissions.ROLE_MANAGE + "')")
    @Transactional
    public void deleteRole(UUID roleId) {
        Role role = roleRepository.findById(roleId)
            .orElseThrow(() -> AppException.notFound("Роль"));
        if (role.isSystem()) {
            throw AppException.badRequest("SYSTEM_ROLE",
                "Системные роли нельзя удалить");
        }
        roleRepository.deleteNonSystemById(roleId);
        log.info("Role deleted: {}", roleId);
    }

    // ----------------------------------------------------------------
    //  Пермиссии роли
    // ----------------------------------------------------------------

    public List<Permission> getAllPermissions() {
        return permissionRepository.findAll();
    }

    public List<Permission> getPermissionsByRole(UUID roleId) {
        return permissionRepository.findByRoleId(roleId);
    }

    /**
     * Полная замена набора пермиссий роли.
     * После обновления сбрасываем кэш всех пользователей с этой ролью.
     */
    @PreAuthorize("@sec.has('" + Permissions.ROLE_MANAGE + "')")
    @Transactional
    public void setRolePermissions(UUID roleId, Set<UUID> permissionIds) {
        roleRepository.findById(roleId)
            .orElseThrow(() -> AppException.notFound("Роль"));

        // Удаляем старые связи
        permissionRepository.findByRoleId(roleId).forEach(p ->
            removePermissionFromRole(roleId, p.getId())
        );

        // Добавляем новые
        permissionIds.forEach(permId -> addPermissionToRole(roleId, permId));

        // Сбрасываем кэш всех пользователей с этой ролью
        evictCacheForRoleUsers(roleId);

        log.info("Permissions updated for role: {}, count: {}", roleId, permissionIds.size());
    }

    // ----------------------------------------------------------------
    //  Назначение ролей пользователям
    // ----------------------------------------------------------------

    @PreAuthorize("@sec.has('" + Permissions.USER_MANAGE + "')")
    @Transactional
    public void assignRole(UUID targetUserId, UUID roleId, User assignedBy) {
        roleRepository.findById(roleId)
            .orElseThrow(() -> AppException.notFound("Роль"));

        if (userRoleRepository.existsByUserIdAndRoleId(targetUserId, roleId)) {
            throw AppException.conflict("ROLE_ALREADY_ASSIGNED",
                "Роль уже назначена этому пользователю");
        }

        UserRole userRole = UserRole.builder()
            .userId(targetUserId)
            .roleId(roleId)
            .assignedAt(Instant.now())
            .assignedBy(assignedBy.getId())
            .build();
        userRoleRepository.save(userRole);

        // Сбрасываем кэш прав назначенного пользователя
        permissionsService.evictAll(targetUserId, TenantContext.get());
        log.info("Role {} assigned to user {} by {}", roleId, targetUserId, assignedBy.getId());
    }

    @PreAuthorize("@sec.has('" + Permissions.USER_MANAGE + "')")
    @Transactional
    public void revokeRole(UUID targetUserId, UUID roleId) {
        userRoleRepository.deleteByUserIdAndRoleId(targetUserId, roleId);
        permissionsService.evictAll(targetUserId, TenantContext.get());
        log.info("Role {} revoked from user {}", roleId, targetUserId);
    }

    @PreAuthorize("@sec.has('" + Permissions.USER_MANAGE + "')")
    @Transactional
    public void setUserRoles(UUID targetUserId, Set<UUID> roleIds, User assignedBy) {
        // Удаляем все текущие роли
        userRoleRepository.deleteAllByUserId(targetUserId);

        // Назначаем новые
        roleIds.forEach(roleId -> {
            UserRole userRole = UserRole.builder()
                .userId(targetUserId)
                .roleId(roleId)
                .assignedAt(Instant.now())
                .assignedBy(assignedBy.getId())
                .build();
            userRoleRepository.save(userRole);
        });

        permissionsService.evictAll(targetUserId, TenantContext.get());
        log.info("Roles updated for user {}: {}", targetUserId, roleIds);
    }

    // ----------------------------------------------------------------
    //  Приватные методы
    // ----------------------------------------------------------------

    private void addPermissionToRole(UUID roleId, UUID permissionId) {
        permissionRepository.findById(permissionId)
            .orElseThrow(() -> AppException.notFound("Пермиссия"));
        rolePermissionRepository.addPermissionToRole(roleId, permissionId);
    }

    private void removePermissionFromRole(UUID roleId, UUID permissionId) {
        rolePermissionRepository.removePermissionFromRole(roleId, permissionId);
    }

    private void evictCacheForRoleUsers(UUID roleId) {
        userRoleRepository.findByUserId(roleId).forEach(ur ->
            permissionsService.evictAll(ur.getUserId(), TenantContext.get())
        );
    }
}
