package com.crm.rbac.config;

/**
 * Константы кодов пермиссий.
 *
 * Используются в двух местах:
 *  1. Бэкенд: @PreAuthorize("@sec.has('CUSTOMER_VIEW')")
 *  2. Фронтенд: получает массив permissions[] из /auth/me
 *     и проверяет перед рендерингом кнопок/маршрутов.
 *
 * При добавлении новой пермиссии:
 *  - добавить константу сюда
 *  - добавить INSERT в Liquibase миграцию tenant/V001__create_tenant_schema.xml
 *  - добавить @PreAuthorize на нужный метод
 */
public final class Permissions {

    private Permissions() {}

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
    public static final String ORDER_DELETE = "ORDER_DELETE";

    // ---- PRODUCTS ----
    public static final String PRODUCT_VIEW   = "PRODUCT_VIEW";
    public static final String PRODUCT_MANAGE = "PRODUCT_MANAGE";

    // ---- USERS ----
    public static final String USER_VIEW   = "USER_VIEW";
    public static final String USER_MANAGE = "USER_MANAGE";

    // ---- ADMIN ----
    public static final String ROLE_MANAGE     = "ROLE_MANAGE";
    public static final String MODULE_SETTINGS = "MODULE_SETTINGS";
    public static final String TENANT_MANAGE   = "TENANT_MANAGE";
}
