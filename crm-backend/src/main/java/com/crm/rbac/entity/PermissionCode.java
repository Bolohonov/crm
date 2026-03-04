package com.crm.rbac.entity;

/**
 * Централизованный реестр всех кодов прав доступа.
 *
 * Используется:
 * - В @PreAuthorize("hasAuthority('CUSTOMER_VIEW')") на бэкенде
 * - Фронтенд получает набор этих кодов в /auth/me и проверяет локально
 *
 * Коды должны совпадать с записями в таблице permissions (seed в Liquibase).
 */
public final class PermissionCode {

    private PermissionCode() {}

    // ---- CUSTOMERS ----
    public static final String CUSTOMER_VIEW   = "CUSTOMER_VIEW";
    public static final String CUSTOMER_CREATE = "CUSTOMER_CREATE";
    public static final String CUSTOMER_EDIT   = "CUSTOMER_EDIT";
    public static final String CUSTOMER_DELETE = "CUSTOMER_DELETE";

    // ---- TASKS ----
    public static final String TASK_VIEW   = "TASK_VIEW";
    public static final String TASK_CREATE = "TASK_CREATE";
    public static final String TASK_EDIT   = "TASK_EDIT";
    public static final String TASK_DELETE = "TASK_DELETE";
    public static final String TASK_ASSIGN = "TASK_ASSIGN";

    // ---- ORDERS ----
    public static final String ORDER_VIEW   = "ORDER_VIEW";
    public static final String ORDER_CREATE = "ORDER_CREATE";
    public static final String ORDER_EDIT   = "ORDER_EDIT";

    // ---- PRODUCTS ----
    public static final String PRODUCT_VIEW   = "PRODUCT_VIEW";
    public static final String PRODUCT_MANAGE = "PRODUCT_MANAGE";

    // ---- ADMIN ----
    public static final String USER_MANAGE    = "USER_MANAGE";
    public static final String ROLE_MANAGE    = "ROLE_MANAGE";
    public static final String MODULE_SETTINGS = "MODULE_SETTINGS";
}
