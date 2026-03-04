package com.crm.rbac.service;

import com.crm.common.exception.AppException;
import com.crm.rbac.dto.PermissionDto;
import com.crm.rbac.dto.RoleDto;
import com.crm.rbac.entity.Permission;
import com.crm.rbac.entity.Role;
import com.crm.rbac.entity.UserRole;
import com.crm.rbac.repository.PermissionRepository;
import com.crm.rbac.repository.RolePermissionRepository;
import com.crm.rbac.repository.RoleRepository;
import com.crm.rbac.repository.UserRoleRepository;
import com.crm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RbacService {

    private final RoleRepository             roleRepository;
    private final PermissionRepository       permissionRepository;
    private final UserRoleRepository         userRoleRepository;
    private final RolePermissionRepository   rolePermissionRepository;
    private final PermissionService          permissionService;

    // ── Роли ─────────────────────────────────────────────────────────

    public List<RoleDto.RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
            .map(this::toRoleDto)
            .collect(Collectors.toList());
    }

    public RoleDto.RoleResponse getRoleById(UUID roleId) {
        Role role = findRoleOrThrow(roleId);
        RoleDto.RoleResponse dto = toRoleDto(role);
        dto.setPermissions(permissionRepository.findByRoleId(roleId)
            .stream().map(this::toPermissionDto).collect(Collectors.toList()));
        return dto;
    }

    @Transactional
    public RoleDto.RoleResponse createRole(RoleDto.CreateRequest request) {
        if (roleRepository.existsByCode(request.getCode())) {
            throw AppException.conflict("ROLE_CODE_EXISTS",
                "Роль с кодом '" + request.getCode() + "' уже существует");
        }

        Role role = Role.builder()
            .code(request.getCode().toUpperCase())
            .name(request.getName())
            .description(request.getDescription())
            .isSystem(false)
            .createdAt(Instant.now())
            .build();

        role = roleRepository.save(role);

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            assignPermissionsToRole(role.getId(), request.getPermissionIds());
        }

        log.info("Role created: {} in schema: {}", role.getCode(), TenantContext.get());
        return getRoleById(role.getId());
    }

    @Transactional
    public RoleDto.RoleResponse updateRolePermissions(UUID roleId, List<UUID> permissionIds) {
        Role role = findRoleOrThrow(roleId);
        rolePermissionRepository.removeAllPermissionsFromRole(roleId);
        assignPermissionsToRole(roleId, permissionIds);
        permissionService.evictAllTenantPermissions();
        log.info("Role permissions updated: {}", role.getCode());
        return getRoleById(roleId);
    }

    @Transactional
    public void deleteRole(UUID roleId) {
        Role role = findRoleOrThrow(roleId);
        if (role.isSystem()) {
            throw AppException.badRequest("SYSTEM_ROLE_DELETE", "Системные роли нельзя удалить");
        }
        roleRepository.deleteNonSystemById(roleId);
        permissionService.evictAllTenantPermissions();
        log.info("Role deleted: {}", roleId);
    }

    // ── Назначение ролей пользователям ───────────────────────────────

    @Transactional
    public void assignRoleToUser(UUID targetUserId, UUID roleId, UUID assignedBy) {
        findRoleOrThrow(roleId);
        boolean alreadyAssigned = userRoleRepository.findByUserId(targetUserId)
            .stream().anyMatch(ur -> ur.getRoleId().equals(roleId));

        if (!alreadyAssigned) {
            userRoleRepository.save(UserRole.builder()
                .userId(targetUserId)
                .roleId(roleId)
                .assignedAt(Instant.now())
                .assignedBy(assignedBy)
                .build());
            permissionService.evictUserPermissions(targetUserId, TenantContext.get());
            log.info("Role {} assigned to user {} by {}", roleId, targetUserId, assignedBy);
        }
    }

    @Transactional
    public void removeRoleFromUser(UUID targetUserId, UUID roleId) {
        userRoleRepository.deleteByUserIdAndRoleId(targetUserId, roleId);
        permissionService.evictUserPermissions(targetUserId, TenantContext.get());
        log.info("Role {} removed from user {}", roleId, targetUserId);
    }

    @Transactional
    public void setUserRoles(UUID targetUserId, List<UUID> roleIds, UUID assignedBy) {
        userRoleRepository.deleteAllByUserId(targetUserId);
        roleIds.forEach(roleId -> userRoleRepository.save(UserRole.builder()
            .userId(targetUserId)
            .roleId(roleId)
            .assignedAt(Instant.now())
            .assignedBy(assignedBy)
            .build()));
        permissionService.evictUserPermissions(targetUserId, TenantContext.get());
        log.info("Roles reset for user {}: {}", targetUserId, roleIds);
    }

    // ── Справочник прав ──────────────────────────────────────────────

    public List<PermissionDto.PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll().stream()
            .map(this::toPermissionDto)
            .collect(Collectors.toList());
    }

    // ── Вспомогательные ──────────────────────────────────────────────

    private void assignPermissionsToRole(UUID roleId, List<UUID> permissionIds) {
        permissionIds.forEach(permId ->
            rolePermissionRepository.addPermissionToRole(roleId, permId));
    }

    private Role findRoleOrThrow(UUID roleId) {
        return roleRepository.findById(roleId)
            .orElseThrow(() -> AppException.notFound("Роль"));
    }

    private RoleDto.RoleResponse toRoleDto(Role role) {
        return RoleDto.RoleResponse.builder()
            .id(role.getId())
            .code(role.getCode())
            .name(role.getName())
            .description(role.getDescription())
            .isSystem(role.isSystem())
            .createdAt(role.getCreatedAt())
            .build();
    }

    private PermissionDto.PermissionResponse toPermissionDto(Permission p) {
        return PermissionDto.PermissionResponse.builder()
            .id(p.getId())
            .code(p.getCode())
            .name(p.getName())
            .description(p.getDescription())
            .module(p.getModule())
            .build();
    }
}
