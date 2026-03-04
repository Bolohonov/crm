package com.crm.migration;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Сервис миграции схемы при onboarding нового тенанта.
 *
 * Вызывается при регистрации нового тенанта — создаёт
 * изолированную PostgreSQL схему и применяет все tenant-миграции.
 *
 * Пример вызова:
 *   tenantMigrationService.createTenantSchema("tenant_a3f7b2c9");
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantMigrationService {

    private final DataSource dataSource;

    // Changelog содержащий только tenant-миграции (V101–V110)
    private static final String TENANT_CHANGELOG = "db/migration/tenant/tenant-changelog.xml";
    private static final String PUBLIC_CHANGELOG  = "db/changelog-master.xml";

    /**
     * Создаёт PostgreSQL схему для нового тенанта и применяет миграции.
     *
     * @param schemaName имя схемы: tenant_{uuid_без_дефисов}
     * @param applyDemoData применить демо-данные (только для demo-тенантов)
     */
    public void createTenantSchema(String schemaName, boolean applyDemoData) {
        log.info("Creating tenant schema: {}", schemaName);

        try (Connection conn = dataSource.getConnection()) {
            // 1. Создаём схему
            try (PreparedStatement ps = conn.prepareStatement(
                    "CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\""
            )) {
                ps.execute();
                log.info("Schema created: {}", schemaName);
            }

            // 2. Устанавливаем search_path → применяем tenant-миграции в нужной схеме
            conn.createStatement().execute("SET search_path TO \"" + schemaName + "\", public");

            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            database.setDefaultSchemaName(schemaName);
            database.setLiquibaseSchemaName(schemaName); // databasechangelog в tenant схеме

            Liquibase liquibase = new Liquibase(
                    TENANT_CHANGELOG,
                    new ClassLoaderResourceAccessor(),
                    database
            );

            // 3. Контекст demo только для демо-тенантов
            String contexts = applyDemoData ? "demo" : "";
            liquibase.update(contexts);

            log.info("Tenant schema migrations applied: schema={}, demo={}", schemaName, applyDemoData);

        } catch (Exception e) {
            log.error("Failed to create tenant schema: {}", schemaName, e);
            throw new TenantMigrationException("Cannot create tenant schema: " + schemaName, e);
        }
    }

    /**
     * Применяет новые миграции к существующей схеме тенанта.
     * Вызывается при обновлении приложения для всех тенантов.
     */
    public void upgradeTenantSchema(String schemaName) {
        log.info("Upgrading tenant schema: {}", schemaName);

        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().execute("SET search_path TO \"" + schemaName + "\", public");

            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            database.setDefaultSchemaName(schemaName);
            database.setLiquibaseSchemaName(schemaName);

            Liquibase liquibase = new Liquibase(
                    TENANT_CHANGELOG,
                    new ClassLoaderResourceAccessor(),
                    database
            );
            // Без demo-контекста при апгрейде существующих тенантов
            liquibase.update("");

            log.info("Tenant schema upgraded: {}", schemaName);

        } catch (Exception e) {
            log.error("Failed to upgrade tenant schema: {}", schemaName, e);
            throw new TenantMigrationException("Cannot upgrade tenant schema: " + schemaName, e);
        }
    }

    public static class TenantMigrationException extends RuntimeException {
        public TenantMigrationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
