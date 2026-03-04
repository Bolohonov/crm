package com.crm.tenant;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

/**
 * Хранит данные текущего тенанта в ThreadLocal.
 *
 * get()       → String schemaName (для DataSource, SQL-запросов)
 * getTenant() → Tenant объект (для бизнес-логики)
 */
@Slf4j
public final class TenantContext {

    static final String PUBLIC_SCHEMA = "public";
    private static final String MDC_KEY = "tenantSchema";

    private static final InheritableThreadLocal<String> CURRENT_SCHEMA =
            new InheritableThreadLocal<>();

    private static final InheritableThreadLocal<Tenant> CURRENT_TENANT =
            new InheritableThreadLocal<>();

    private TenantContext() {}

    public static void set(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            log.warn("Attempt to set blank tenant schema, falling back to public");
            CURRENT_SCHEMA.set(PUBLIC_SCHEMA);
        } else {
            CURRENT_SCHEMA.set(schemaName);
        }
        MDC.put(MDC_KEY, schemaName);
        log.debug("Tenant context set to: {}", schemaName);
    }

    public static void setTenant(Tenant tenant) {
        CURRENT_TENANT.set(tenant);
        if (tenant != null) {
            set(tenant.getSchemaName());
        }
    }

    /** Возвращает schemaName текущего тенанта (или "public"). */
    public static String get() {
        String schema = CURRENT_SCHEMA.get();
        return (schema != null) ? schema : PUBLIC_SCHEMA;
    }

    /** Возвращает Tenant объект текущего тенанта. Может быть null для публичных операций. */
    public static Tenant getTenant() {
        return CURRENT_TENANT.get();
    }

    public static boolean isSet() {
        return CURRENT_SCHEMA.get() != null;
    }

    public static void clear() {
        CURRENT_SCHEMA.remove();
        CURRENT_TENANT.remove();
        MDC.remove(MDC_KEY);
        log.debug("Tenant context cleared");
    }

    public static String getOrPublic() {
        return get();
    }
}
