package com.crm.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Сервис сброса демо-схемы.
 *
 * Вызывается из DemoResetController по внутреннему endpoint-у.
 * CronJob в k8s дёргает этот endpoint раз в ночь через curl.
 *
 * Алгоритм:
 *  1. DROP SCHEMA tenant_demo CASCADE   — удаляем всё
 *  2. createTenantSchema(..., true)     — пересоздаём с demo seed
 *
 * public.tenants и public.users_global НЕ трогаем —
 * demo-пользователь остаётся, старые refresh-токены станут
 * невалидны только после их естественного истечения (7 дней),
 * но это для демо некритично.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoResetService {

    private static final String DEMO_SCHEMA = "tenant_demo";

    private final JdbcTemplate            jdbcTemplate;
    private final TenantMigrationService  tenantMigrationService;

    public void reset() {
        log.info("Demo schema reset started");
        long start = System.currentTimeMillis();

        // 1. DROP — CASCADE убирает все таблицы, индексы, функции внутри схемы
        log.info("Dropping schema: {}", DEMO_SCHEMA);
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + DEMO_SCHEMA + "\" CASCADE");

        // 2. Пересоздаём через TenantMigrationService с context=demo
        //    Это накатит V101__init.xml включая все V110 demo-seed changeset-ы
        log.info("Recreating schema: {}", DEMO_SCHEMA);
        tenantMigrationService.createTenantSchema(DEMO_SCHEMA, true);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Demo schema reset completed in {}ms", elapsed);
    }
}
