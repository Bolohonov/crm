package com.crm.rbac.controller;

import com.crm.rbac.dto.RoleUpdateRequest;

import com.crm.rbac.dto.RoleCreateRequest;

import com.crm.rbac.dto.PermissionResponse;

import com.crm.rbac.dto.SetPermissionsRequest;
import com.crm.rbac.dto.SetUserRolesRequest;

import com.crm.common.response.ApiResponse;
import com.crm.rbac.config.Permissions;
import com.crm.rbac.entity.Permission;
import com.crm.rbac.entity.Role;
import com.crm.rbac.repository.ModuleSettingsRepository;
import com.crm.rbac.service.RoleService;
import com.crm.rbac.service.UserPermissionsService;
import com.crm.tenant.TenantContext;
import com.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API для управления ролями и пермиссиями.
 * Все эндпоинты требуют JWT и соответствующих прав.
 *
 * GET  /roles                           — список всех ролей тенанта
 * POST /roles                           — создать роль [ROLE_MANAGE]
 * PUT  /roles/{id}                      — обновить роль [ROLE_MANAGE]
 * DEL  /roles/{id}                      — удалить роль [ROLE_MANAGE]
 * GET  /roles/{id}/permissions          — пермиссии роли
 * PUT  /roles/{id}/permissions          — заменить пермиссии роли [ROLE_MANAGE]
 * GET  /permissions                     — все пермиссии системы
 * PUT  /users/{userId}/roles            — назначить роли пользователю [USER_MANAGE]
 * GET  /modules                         — настройки модулей [isAdmin]
 * PUT  /modules/{code}                  — включить/выключить модуль [MODULE_SETTINGS]
 */
@RestController
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final UserPermissionsService permissionsService;
    private final ModuleSettingsRepository moduleSettingsRepository;

    // ----------------------------------------------------------------
    //  Роли
    // ----------------------------------------------------------------

    @GetMapping("/roles")
    @PreAuthorize("@sec.isAdmin()")
    public ResponseEntity<ApiResponse<List<Role>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.ok(roleService.getAllRoles()));
    }

    @PostMapping("/roles")
    @PreAuthorize("@sec.has('" + Permissions.ROLE_MANAGE + "')")
    public ResponseEntity<ApiResponse<Role>> createRole(
            @Valid @RequestBody RoleCreateRequest request) {

        Role role = roleService.createRole(
            request.getCode(), request.getName(), request.getDescription()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(role));
    }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("@sec.has('" + Permissions.ROLE_MANAGE + "')")
    public ResponseEntity<ApiResponse<Role>> updateRole(
            @PathVariable UUID roleId,
            @Valid @RequestBody RoleUpdateRequest request) {

        Role role = roleService.updateRole(roleId, request.getName(), request.getDescription());
        return ResponseEntity.ok(ApiResponse.ok(role));
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("@sec.has('" + Permissions.ROLE_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID roleId) {
        roleService.deleteRole(roleId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ----------------------------------------------------------------
    //  Пермиссии роли
    // ----------------------------------------------------------------

    @GetMapping("/roles/{roleId}/permissions")
    @PreAuthorize("@sec.isAdmin()")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getRolePermissions(
            @PathVariable UUID roleId) {

        List<PermissionResponse> permissions =
            roleService.getPermissionsByRole(roleId).stream()
                .map(this::toPermissionResponse)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(permissions));
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("@sec.has('" + Permissions.ROLE_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> setRolePermissions(
            @PathVariable UUID roleId,
            @RequestBody SetPermissionsRequest request) {

        roleService.setRolePermissions(roleId, request.getPermissionIds());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ----------------------------------------------------------------
    //  Все пермиссии системы (для UI выбора при настройке роли)
    // ----------------------------------------------------------------

    @GetMapping("/permissions")
    @PreAuthorize("@sec.isAdmin()")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> getAllPermissions() {
        List<PermissionResponse> permissions =
            roleService.getAllPermissions().stream()
                .map(this::toPermissionResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(permissions));
    }

    // ----------------------------------------------------------------
    //  Роли пользователей
    // ----------------------------------------------------------------

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("@sec.has('" + Permissions.USER_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> setUserRoles(
            @PathVariable UUID userId,
            @RequestBody SetUserRolesRequest request,
            @AuthenticationPrincipal User currentUser) {

        roleService.setUserRoles(userId, request.getRoleIds(), currentUser);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ----------------------------------------------------------------
    //  Настройки модулей
    // ----------------------------------------------------------------

    @GetMapping("/modules")
    @PreAuthorize("@sec.isAdmin()")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getModules() {
        return ResponseEntity.ok(ApiResponse.ok(
            moduleSettingsRepository.findAllModules()
        ));
    }

    @PutMapping("/modules/{moduleCode}")
    @PreAuthorize("@sec.has('" + Permissions.MODULE_SETTINGS + "')")
    public ResponseEntity<ApiResponse<Void>> setModuleEnabled(
            @PathVariable String moduleCode,
            @RequestParam boolean enabled) {

        moduleSettingsRepository.setModuleEnabled(moduleCode, enabled);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ----------------------------------------------------------------
    //  Mapper
    // ----------------------------------------------------------------

    private PermissionResponse toPermissionResponse(Permission p) {
        return PermissionResponse.builder()
            .id(p.getId())
            .code(p.getCode())
            .name(p.getName())
            .description(p.getDescription())
            .module(p.getModule())
            .build();
    }
}
