package com.crm.rbac.service;

import com.crm.rbac.entity.PermissionCode;
import com.crm.rbac.repository.PermissionRepository;
import com.crm.rbac.repository.UserRoleRepository;
import com.crm.tenant.TenantContext;
import com.crm.user.entity.User;
import com.crm.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Сервис загрузки прав доступа пользователя.
 *
 * Кэширование в Redis:
 * - Ключ: "userPermissions::{tenantSchema}::{userId}"
 * - TTL: 5 минут (настраивается в RedisConfig)
 * - Инвалидируется при изменении ролей пользователя или прав роли
 *
 * Для ADMIN пользователя (владельца тенанта) возвращаем все права —
 * он не ограничен RBAC внутри своего тенанта.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** Все права для администратора тенанта */
    private static final Set<String> ALL_PERMISSIONS = Arrays.stream(
        PermissionCode.class.getDeclaredFields()
    ).map(f -> {
        try { return (String) f.get(null); }
        catch (IllegalAccessException e) { return null; }
    }).collect(Collectors.toSet());

    private final UserRoleRepository userRoleRepository;
    private final PermissionRepository permissionRepository;

    /**
     * Возвращает набор кодов прав для пользователя.
     * Кэшируется в Redis, инвалидируется при смене ролей.
     */
    @Cacheable(value = "userPermissions", key = "#tenantSchema + '::' + #userId")
    public Set<String> getPermissions(UUID userId, String tenantSchema, boolean isAdmin) {
        if (isAdmin) {
            log.debug("Admin user {} — returning all permissions", userId);
            return ALL_PERMISSIONS;
        }

        // Загружаем роли пользователя
        List<UUID> roleIds = userRoleRepository.findByUserId(userId)
            .stream()
            .map(ur -> ur.getRoleId())
            .collect(Collectors.toList());

        if (roleIds.isEmpty()) {
            log.debug("User {} has no roles assigned", userId);
            return Set.of();
        }

        // Собираем права из всех ролей пользователя
        Set<String> permissions = roleIds.stream()
            .flatMap(roleId -> permissionRepository.findByRoleId(roleId).stream())
            .map(p -> p.getCode())
            .collect(Collectors.toSet());

        log.debug("User {} has {} permissions", userId, permissions.size());
        return permissions;
    }

    /**
     * Инвалидирует кэш прав пользователя.
     * Вызывается при изменении ролей пользователя или прав роли.
     */
    @CacheEvict(value = "userPermissions", key = "#tenantSchema + '::' + #userId")
    public void evictUserPermissions(UUID userId, String tenantSchema) {
        log.debug("Permission cache evicted for user: {}, schema: {}", userId, tenantSchema);
    }

    /**
     * Инвалидирует кэш для всех пользователей тенанта
     * (при изменении прав роли затрагивает всех кто её имеет).
     */
    @CacheEvict(value = "userPermissions", allEntries = true)
    public void evictAllTenantPermissions() {
        log.debug("All permission cache entries evicted");
    }

    /**
     * Проверяет право текущего аутентифицированного пользователя.
     * Используется в Spring Security @PreAuthorize через SpEL:
     *   @PreAuthorize("@permissionService.hasPermission('CUSTOMER_VIEW')")
     */
    public boolean hasPermission(String permissionCode) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;

        if (auth.getPrincipal() instanceof User user) {
            Set<String> perms = getPermissions(
                user.getId(),
                TenantContext.get(),
                user.isAdmin()
            );
            return perms.contains(permissionCode);
        }
        return false;
    }
}
