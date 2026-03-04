package com.crm.rbac.config;

import com.crm.tenant.TenantContext;
import com.crm.user.entity.User;
import com.crm.rbac.service.UserPermissionsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Бин для использования в @PreAuthorize выражениях.
 *
 * Использование на методах сервисов и контроллеров:
 *
 *   @PreAuthorize("@sec.has('CUSTOMER_VIEW')")
 *   public List<CustomerDto> getCustomers() { ... }
 *
 *   @PreAuthorize("@sec.hasAny('TASK_EDIT', 'TASK_DELETE')")
 *   public void deleteTask(UUID id) { ... }
 *
 *   @PreAuthorize("@sec.isAdmin()")
 *   public void manageUsers() { ... }
 *
 * Имя бина "sec" — короткое, чтобы аннотации были лаконичными.
 * Проверка идёт в два этапа:
 *  1. TENANT_ADMIN — имеет все права автоматически (без обращения к БД/Redis)
 *  2. Остальные — через UserPermissionsService (Redis-кэш → БД)
 */
@Slf4j
@Component("sec")
@RequiredArgsConstructor
public class SecurityExpressionService {

    private final UserPermissionsService permissionsService;

    /**
     * Проверяет наличие пермиссии у текущего пользователя.
     */
    public boolean has(String permissionCode) {
        User user = getCurrentUser();
        if (user == null) return false;

        // TENANT_ADMIN имеет полный доступ — не проверяем пермиссии
        if (user.isAdmin()) return true;

        return permissionsService.hasPermission(
            user.getId(),
            TenantContext.get(),
            permissionCode
        );
    }

    /**
     * Проверяет наличие хотя бы одной из перечисленных пермиссий.
     */
    public boolean hasAny(String... permissionCodes) {
        User user = getCurrentUser();
        if (user == null) return false;
        if (user.isAdmin()) return true;

        Set<String> userPerms = permissionsService.getPermissionCodes(
            user.getId(), TenantContext.get()
        );
        for (String code : permissionCodes) {
            if (userPerms.contains(code)) return true;
        }
        return false;
    }

    /**
     * Проверяет наличие всех перечисленных пермиссий.
     */
    public boolean hasAll(String... permissionCodes) {
        User user = getCurrentUser();
        if (user == null) return false;
        if (user.isAdmin()) return true;

        Set<String> userPerms = permissionsService.getPermissionCodes(
            user.getId(), TenantContext.get()
        );
        for (String code : permissionCodes) {
            if (!userPerms.contains(code)) return false;
        }
        return true;
    }

    /**
     * Является ли текущий пользователь администратором тенанта.
     */
    public boolean isAdmin() {
        User user = getCurrentUser();
        return user != null && user.isAdmin();
    }

    /**
     * Является ли текущий пользователь владельцем ресурса.
     * Используется для проверок типа "можно редактировать только свои задачи".
     */
    public boolean isOwner(java.util.UUID resourceOwnerId) {
        User user = getCurrentUser();
        return user != null && user.getId().equals(resourceOwnerId);
    }

    /**
     * Владелец или имеет пермиссию — типичный паттерн для задач.
     * @PreAuthorize("@sec.isOwnerOrHas(#task.authorId, 'TASK_EDIT')")
     */
    public boolean isOwnerOrHas(java.util.UUID ownerId, String permissionCode) {
        return isOwner(ownerId) || has(permissionCode);
    }

    // ---- private ----

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        if (auth.getPrincipal() instanceof User user) return user;
        return null;
    }
}
