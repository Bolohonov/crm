package com.crm.tenant.controller;

import com.crm.common.response.ApiResponse;
import com.crm.rbac.config.Permissions;
import com.crm.tenant.dto.TenantDto;
import com.crm.tenant.service.TenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Настройки тенанта и управление модулями.
 *
 * GET    /api/v1/tenant                          — профиль тенанта
 * PUT    /api/v1/tenant/settings                 — обновить настройки [TENANT_MANAGE]
 * GET    /api/v1/tenant/modules                  — список модулей и их статус
 * PATCH  /api/v1/tenant/modules/{code}           — включить/выключить модуль [MODULE_SETTINGS]
 */
@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    // ── Профиль тенанта ───────────────────────────────────────────
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TenantDto.TenantResponse>> getProfile() {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.getProfile()));
    }

    // ── Обновить настройки ────────────────────────────────────────
    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('" + Permissions.TENANT_MANAGE + "')")
    public ResponseEntity<ApiResponse<TenantDto.TenantResponse>> updateSettings(
            @Valid @RequestBody TenantDto.UpdateSettingsRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(tenantService.updateSettings(request)));
    }

    // ── Модули ────────────────────────────────────────────────────
    @GetMapping("/modules")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TenantDto.ModuleResponse>>> getModules() {
        return ResponseEntity.ok(ApiResponse.ok(tenantService.getModules()));
    }

    @PatchMapping("/modules/{code}")
    @PreAuthorize("hasAuthority('" + Permissions.MODULE_SETTINGS + "')")
    public ResponseEntity<ApiResponse<Void>> setModuleEnabled(
            @PathVariable String code,
            @RequestBody TenantDto.SetModuleRequest request) {

        tenantService.setModuleEnabled(code, request.isEnabled());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
