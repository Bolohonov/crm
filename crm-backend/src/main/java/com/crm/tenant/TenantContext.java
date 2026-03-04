package com.crm.tenant;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Хранит имя схемы текущего тенанта в ThreadLocal.
 *
 * Жизненный цикл:
 *   1. JwtAuthenticationFilter извлекает tenantSchema из JWT-токена
 *   2. Устанавливает его через TenantContext.set()
 *   3. TenantAwareDataSource читает значение при каждом запросе к БД
 *   4. После обработки запроса — TenantContext.clear() в том же фильтре
 *
 * ThreadLocal безопасен для использования в многопоточной среде Spring,
 * так как каждый HTTP-запрос обрабатывается в отдельном потоке.
 */
@Slf4j
public final class TenantContext {

    private static final String PUBLIC_SCHEMA = "public";
    private static final String MDC_KEY = "tenantSchema";

    // InheritableThreadLocal позволяет дочерним потокам наследовать контекст
    private static final InheritableThreadLocal<String> CURRENT_SCHEMA =
            new InheritableThreadLocal<>();

    private TenantContext() {
        // Utility class — не инстанциируется
    }

    /**
     * Устанавливает схему тенанта для текущего потока.
     *
     * @param schemaName имя схемы (например, tenant_550e8400...)
     */
    public static void set(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            log.warn("Attempt to set blank tenant schema, falling back to public");
            CURRENT_SCHEMA.set(PUBLIC_SCHEMA);
        } else {
            CURRENT_SCHEMA.set(schemaName);
        }
        // Добавляем в MDC для логирования — видно в каждой строке лога
        MDC.put(MDC_KEY, schemaName);
        log.debug("Tenant context set to: {}", schemaName);
    }

    /**
     * Возвращает текущую схему тенанта.
     * Если не установлена — возвращает "public" (для глобальных операций).
     */
    public static String get() {
        String schema = CURRENT_SCHEMA.get();
        return (schema != null) ? schema : PUBLIC_SCHEMA;
    }

    /**
     * Проверяет, установлен ли контекст тенанта.
     */
    public static boolean isSet() {
        return CURRENT_SCHEMA.get() != null;
    }

    /**
     * Очищает контекст. ОБЯЗАТЕЛЬНО вызывать после обработки запроса,
     * иначе возможна утечка контекста между запросами в пуле потоков.
     */
    public static void clear() {
        CURRENT_SCHEMA.remove();
        MDC.remove(MDC_KEY);
        log.debug("Tenant context cleared");
    }

    /**
     * Возвращает текущую схему или публичную схему если тенант не установлен.
     * Используется для операций которые могут работать в обоих контекстах.
     */
    public static String getOrPublic() {
        return get();
    }
}
