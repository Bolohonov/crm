package com.crm.tenant;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Управляет PostgreSQL-схемами тенантов.
 *
 * Вызывается при:
 *  - регистрации нового администратора → provisionTenantSchema()
 *  - обновлении системы → updateAllTenantSchemas()
 *  - удалении аккаунта → dropTenantSchema() (осторожно!)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSchemaService {

    private static final String SCHEMA_PREFIX = "tenant_";
    private static final String TENANT_CHANGELOG =
            "db/migration/tenant/tenant-changelog.xml";

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Создаёт схему и применяет миграции для нового тенанта.
     * Выполняется в отдельной транзакции чтобы не блокировать регистрацию пользователя.
     *
     * @param tenantId UUID из public.tenants
     * @return имя созданной схемы
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String provisionTenantSchema(UUID tenantId) {
        String schemaName = buildSchemaName(tenantId);
        log.info("Provisioning tenant schema: {}", schemaName);

        try {
            applyMigrations(schemaName); // создание схемы внутри
            log.info("Tenant schema provisioned successfully: {}", schemaName);
            return schemaName;
        } catch (Exception e) {
            log.error("Failed to provision tenant schema: {}. Rolling back.", schemaName, e);
            safeDropSchema(schemaName);
            throw new TenantProvisioningException(
                    "Failed to provision schema for tenant: " + tenantId, e
            );
        }
    }

    /**
     * Применяет новые миграции к существующей схеме.
     * Liquibase пропустит уже применённые changeSet'ы.
     */
    public void updateTenantSchema(String schemaName) {
        validateSchemaName(schemaName);
        log.info("Updating tenant schema: {}", schemaName);
        applyMigrations(schemaName);
    }

    /**
     * Удаляет схему тенанта. Используется только при полном удалении аккаунта.
     * НЕОБРАТИМО — все данные тенанта будут потеряны.
     */
    @Transactional
    public void dropTenantSchema(String schemaName) {
        validateSchemaName(schemaName);
        log.warn("DROPPING tenant schema: {}. All data will be permanently lost!", schemaName);
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        log.info("Tenant schema dropped: {}", schemaName);
    }

    /**
     * Проверяет, существует ли схема в БД.
     */
    public boolean schemaExists(String schemaName) {
        Integer count = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM information_schema.schemata
            WHERE schema_name = ?
            """,
            Integer.class,
            schemaName
        );
        return count != null && count > 0;
    }

    // ----------------------------------------------------------------
    //  Вспомогательные методы
    // ----------------------------------------------------------------

    private void createSchema(String schemaName) {
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + schemaName);
        log.debug("Schema created or already exists: {}", schemaName);
    }

    private void applyMigrations(String schemaName) {
        try (Connection connection = dataSource.getConnection()) {
            // Сначала создаём схему в этом же соединении
            connection.createStatement().execute(
                    "CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\""
            );

            connection.createStatement().execute(
                    "SET search_path TO \"" + schemaName + "\", public"
            );

            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));

            database.setDefaultSchemaName(schemaName);
            database.setLiquibaseSchemaName(schemaName);

            try (Liquibase liquibase = new Liquibase(
                    TENANT_CHANGELOG,
                    new ClassLoaderResourceAccessor(),
                    database
            )) {
                liquibase.update("");
                log.debug("Migrations applied to schema: {}", schemaName);
            }

        } catch (SQLException | LiquibaseException e) {
            throw new TenantProvisioningException(
                    "Failed to apply Liquibase migrations to schema: " + schemaName, e
            );
        }
    }

    private void safeDropSchema(String schemaName) {
        try {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schemaName + " CASCADE");
        } catch (Exception e) {
            log.error("Failed to rollback schema: {}", schemaName, e);
        }
    }

    /**
     * Строит имя схемы из UUID тенанта.
     * UUID дефисы заменяются на подчёркивания для совместимости с SQL.
     */
    public static String buildSchemaName(UUID tenantId) {
        return SCHEMA_PREFIX + tenantId.toString().replace("-", "_");
    }

    private void validateSchemaName(String schemaName) {
        if (schemaName == null || !schemaName.startsWith(SCHEMA_PREFIX)) {
            throw new IllegalArgumentException(
                "Schema name must start with '" + SCHEMA_PREFIX + "', got: " + schemaName
            );
        }
        if (!schemaName.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(
                "Invalid characters in schema name: " + schemaName
            );
        }
    }
}
