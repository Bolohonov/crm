package com.crm.auth.service;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Сервис сброса демо-данных CRM.
 *
 * Каждую ночь в 03:00 (k8s CronJob):
 *   1. DROP SCHEMA tenant_demo CASCADE
 *   2. CREATE SCHEMA tenant_demo
 *   3. Накатываем tenant-миграции с context=demo (60 клиентов, 80 заказов, 50 задач)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoResetService {

    private static final String DEMO_SCHEMA      = "tenant_demo";
    private static final String TENANT_CHANGELOG = "db/migration/tenant/tenant-changelog.xml";

    private final JdbcTemplate jdbcTemplate;
    private final DataSource   dataSource;

    public void reset() {
        log.info("CRM demo reset started");
        long start = System.currentTimeMillis();

        // 1. Удаляем схему со всеми данными
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS \"" + DEMO_SCHEMA + "\" CASCADE");
        log.debug("Schema dropped: {}", DEMO_SCHEMA);

        // 2. Пересоздаём и накатываем миграции с context=demo
        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("CREATE SCHEMA IF NOT EXISTS \"" + DEMO_SCHEMA + "\"");
            conn.createStatement().execute("SET search_path TO \"" + DEMO_SCHEMA + "\", public");

            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            database.setDefaultSchemaName(DEMO_SCHEMA);
            database.setLiquibaseSchemaName(DEMO_SCHEMA);

            Liquibase liquibase = new Liquibase(
                    TENANT_CHANGELOG,
                    new ClassLoaderResourceAccessor(),
                    database
            );
            liquibase.update("demo");

            log.info("CRM demo reset completed in {}ms", System.currentTimeMillis() - start);

        } catch (Exception e) {
            log.error("CRM demo reset failed", e);
            throw new RuntimeException("Demo reset failed", e);
        }
    }
}
