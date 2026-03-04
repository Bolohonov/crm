package com.crm.rbac.service;

import com.crm.rbac.repository.PermissionRepository;
import com.crm.rbac.repository.RoleRepository;
import com.crm.rbac.entity.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Сервис загрузки прав пользователя.
 *
 * Кэширование через Redis (@Cacheable):
 *  - ключ: userPermissions::<tenantSchema>::<userId>
 *  - TTL: 5 минут (задан в RedisConfig)
 *  - сброс: при изменении ролей пользователя или прав роли
 *
 * Почему Redis, а не in-memory (Caffeine)?
 * При горизонтальном масштабировании (несколько pod'ов) кэш должен
 * быть общим — иначе изменение роли на pod A не сбросит кэш на pod B.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionsService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    /**
     * Возвращает набор кодов пермиссий пользователя.
     * Результат кэшируется в Redis на 5 минут.
     *
     * Важно: метод вызывается уже в контексте нужного тенанта
     * (search_path установлен JwtAuthenticationFilter).
     */
    @Cacheable(
        cacheNames = "userPermissions",
        key = "#tenantSchema + '::' + #userId"
    )
    public Set<String> getPermissionCodes(UUID userId, String tenantSchema) {
        log.debug("Loading permissions from DB for user: {}, schema: {}", userId, tenantSchema);
        Set<String> codes = permissionRepository.findPermissionCodesByUserId(userId);
        log.debug("Loaded {} permissions for user: {}", codes.size(), userId);
        return codes;
    }

    /**
     * Роли пользователя — для отображения в UI и панели администратора.
     */
    @Cacheable(
        cacheNames = "userPermissions",
        key = "'roles::' + #tenantSchema + '::' + #userId"
    )
    public List<Role> getUserRoles(UUID userId, String tenantSchema) {
        return roleRepository.findRolesByUserId(userId);
    }

    /**
     * Проверяет наличие конкретной пермиссии.
     */
    public boolean hasPermission(UUID userId, String tenantSchema, String permissionCode) {
        return getPermissionCodes(userId, tenantSchema).contains(permissionCode);
    }

    /**
     * Сбрасывает кэш прав конкретного пользователя.
     * Вызывается при изменении ролей пользователя.
     */
    @CacheEvict(
        cacheNames = "userPermissions",
        key = "#tenantSchema + '::' + #userId"
    )
    public void evictUserPermissions(UUID userId, String tenantSchema) {
        log.debug("Permissions cache evicted for user: {}, schema: {}", userId, tenantSchema);
    }

    /**
     * Сбрасывает кэш ролей пользователя.
     */
    @CacheEvict(
        cacheNames = "userPermissions",
        key = "'roles::' + #tenantSchema + '::' + #userId"
    )
    public void evictUserRoles(UUID userId, String tenantSchema) {
        log.debug("Roles cache evicted for user: {}", userId);
    }

    /**
     * Полный сброс кэша прав и ролей пользователя.
     * Используется при назначении/снятии роли.
     */
    public void evictAll(UUID userId, String tenantSchema) {
        evictUserPermissions(userId, tenantSchema);
        evictUserRoles(userId, tenantSchema);
    }
}
