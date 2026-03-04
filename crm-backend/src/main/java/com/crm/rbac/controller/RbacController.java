package com.crm.rbac.controller;

import com.crm.common.response.ApiResponse;
import com.crm.rbac.dto.*;
import com.crm.rbac.entity.PermissionCode;
import com.crm.rbac.service.PermissionService;
import com.crm.rbac.service.RbacService;
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
import java.util.Set;
import java.util.UUID;

/**
 * API управления ролями и правами доступа.
 * Доступно только администратору тенанта (USER_MANAGE, ROLE_MANAGE).
 *
 * GET  /rbac/roles                        — список ролей
 * GET  /rbac/roles/{id}                   — детали роли с правами
 * POST /rbac/roles                        — создать роль
 * PUT  /rbac/roles/{id}/permissions       — обновить права роли
 * DELETE /rbac/roles/{id}                 — удалить роль
 * GET  /rbac/permissions                  — все доступные права
 * GET  /rbac/my-permissions              — права текущего пользователя
 * PUT  /rbac/users/{userId}/roles         — задать роли пользователю
 */
@RestController
@RequestMapping("/rbac")
@RequiredArgsConstructor
public class RbacController {

    private final RbacService rbacService;
    private final PermissionService permissionService;

    // ---- Роли ----

    @GetMapping("/roles")
    @PreAuthorize("@permissionService.hasPermission('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<List<RoleDto>>> getAllRoles() {
        return ResponseEntity.ok(ApiResponse.ok(rbacService.getAllRoles()));
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("@permissionService.hasPermission('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleDto>> getRole(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(rbacService.getRoleById(id)));
    }

    @PostMapping("/roles")
    @PreAuthorize("@permissionService.hasPermission('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleDto>> createRole(
            @Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok(rbacService.createRole(request)));
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("@permissionService.hasPermission('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<RoleDto>> updateRolePermissions(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ResponseEntity.ok(
            ApiResponse.ok(rbacService.updateRolePermissions(id, request.getPermissionIds()))
        );
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("@permissionService.hasPermission('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID id) {
        rbacService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ---- Права ----

    @GetMapping("/permissions")
    @PreAuthorize("@permissionService.hasPermission('ROLE_MANAGE')")
    public ResponseEntity<ApiResponse<List<PermissionDto>>> getAllPermissions() {
        return ResponseEntity.ok(ApiResponse.ok(rbacService.getAllPermissions()));
    }

    /**
     * Права текущего пользователя — вызывается фронтендом при инициализации.
     * Результат кэшируется в Redis.
     */
    @GetMapping("/my-permissions")
    public ResponseEntity<ApiResponse<Set<String>>> getMyPermissions(
            @AuthenticationPrincipal User currentUser) {

        Set<String> permissions = permissionService.getPermissions(
            currentUser.getId(),
            TenantContext.get(),
            currentUser.isAdmin()
        );
        return ResponseEntity.ok(ApiResponse.ok(permissions));
    }

    // ---- Назначение ролей пользователям ----

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("@permissionService.hasPermission('USER_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> setUserRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody SetUserRolesRequest request,
            @AuthenticationPrincipal User currentUser) {

        rbacService.setUserRoles(userId, request.getRoleIds(), currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
