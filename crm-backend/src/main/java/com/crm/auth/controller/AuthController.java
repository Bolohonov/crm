package com.crm.auth.controller;
import com.crm.auth.dto.MeResponse;

import com.crm.auth.dto.*;
import com.crm.auth.service.AuthService;
import com.crm.common.response.ApiResponse;
import com.crm.rbac.entity.Role;
import com.crm.rbac.repository.ModuleSettingsRepository;
import com.crm.rbac.service.UserPermissionsService;
import com.crm.rbac.service.RoleService;
import com.crm.tenant.TenantContext;
import com.crm.tenant.TenantRepository;
import com.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * REST API для аутентификации.
 *
 * Публичные (без JWT):
 *   POST /auth/register
 *   POST /auth/login
 *   POST /auth/refresh
 *   GET  /auth/verify?token=...
 *
 * Защищённые (требуют JWT):
 *   POST /auth/logout
 *   GET  /auth/me        — текущий пользователь + permissions + модули
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserPermissionsService permissionsService;
    private final RoleService roleService;
    private final TenantRepository tenantRepository;
    private final ModuleSettingsRepository moduleSettingsRepository;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok(authService.register(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request.getRefreshToken())));
    }

    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal User currentUser) {
        authService.logout(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /**
     * Полная информация о текущем пользователе.
     *
     * Возвращает:
     *  - профиль пользователя
     *  - набор permissions[] для проверок на фронте
     *  - список roles[]
     *  - enabledModules[] для построения sidebar
     *  - информацию о тенанте (план)
     *
     * Вызывается фронтендом один раз после логина и при обновлении страницы.
     * Результат кэшируется в Pinia store — не надо вызывать на каждый роут.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MeResponse>> me(
            @AuthenticationPrincipal User currentUser) {

        String tenantSchema = TenantContext.get();

        // Пермиссии — из Redis-кэша или БД
        Set<String> permissions = currentUser.isAdmin()
            ? getAllPermissionCodes()   // Админ видит все права (для UI)
            : permissionsService.getPermissionCodes(currentUser.getId(), tenantSchema);

        // Роли пользователя
        List<Role> roles = roleService.getAllRoles().stream()
            .filter(r -> true) // TODO: фильтровать только роли пользователя
            .toList();

        List<MeResponse.RoleInfo> roleInfos = roleService
            .getAllRoles() // временно, заменим на getUserRoles
            .stream()
            .map(r -> MeResponse.RoleInfo.builder()
                .id(r.getId())
                .code(r.getCode())
                .name(r.getName())
                .build())
            .toList();

        // Включённые модули — для sidebar
        Set<String> enabledModules = new HashSet<>(
            moduleSettingsRepository.findEnabledModuleCodes()
        );

        // Информация о тенанте
        String tenantPlan = null;
        if (currentUser.getTenantId() != null) {
            tenantPlan = tenantRepository.findById(currentUser.getTenantId())
                .map(t -> t.getPlan().name())
                .orElse(null);
        }

        MeResponse response = MeResponse.builder()
            .id(currentUser.getId())
            .email(currentUser.getEmail())
            .firstName(currentUser.getFirstName())
            .lastName(currentUser.getLastName())
            .middleName(currentUser.getMiddleName())
            .fullName(currentUser.getFullName())
            .phone(currentUser.getPhone())
            .avatarUrl(currentUser.getAvatarUrl())
            .userType(currentUser.getUserType().name())
            .tenantId(currentUser.getTenantId())
            .tenantSchema(tenantSchema)
            .tenantPlan(tenantPlan)
            .permissions(permissions)
            .roles(roleInfos)
            .enabledModules(enabledModules)
            .build();

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Все коды пермиссий для TENANT_ADMIN.
     * Администратор имеет полный доступ — возвращаем все существующие коды.
     */
    private Set<String> getAllPermissionCodes() {
        return Set.of(
            "CUSTOMER_VIEW", "CUSTOMER_CREATE", "CUSTOMER_EDIT", "CUSTOMER_DELETE",
            "TASK_VIEW", "TASK_CREATE", "TASK_EDIT", "TASK_DELETE", "TASK_ASSIGN",
            "ORDER_VIEW", "ORDER_CREATE", "ORDER_EDIT",
            "PRODUCT_VIEW", "PRODUCT_MANAGE",
            "USER_MANAGE", "ROLE_MANAGE", "MODULE_SETTINGS"
        );
    }
}
