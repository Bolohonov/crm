package com.crm.tenant;

import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

/**
 * Мультитенантный DataSource.
 *
 * Стратегия: один физический пул соединений HikariCP + динамический search_path.
 * При каждом получении соединения устанавливается SET search_path TO <schema>, public
 * чтобы JPA/JDBC запросы работали с таблицами нужного тенанта.
 *
 * Альтернатива — отдельный пул на тенант, но это тяжело при большом числе тенантов.
 * Для нашего масштаба (демо → коммерция) один пул с search_path — оптимально.
 */
@Slf4j
public class TenantAwareDataSource extends AbstractRoutingDataSource {

    /**
     * AbstractRoutingDataSource вызывает этот метод для определения ключа DataSource.
     * Мы используем один DataSource, поэтому возвращаем константу.
     * Реальное переключение схемы происходит в getConnection().
     */
    @Override
    protected Object determineCurrentLookupKey() {
        // Всегда используем один пул — ключ не важен
        return "default";
    }

    /**
     * Переопределяем getConnection чтобы устанавливать search_path
     * перед возвратом соединения вызывающему коду.
     */
    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = super.getConnection();
        setSearchPath(connection);
        return connection;
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        Connection connection = super.getConnection(username, password);
        setSearchPath(connection);
        return connection;
    }

    /**
     * Устанавливает PostgreSQL search_path для соединения.
     * Порядок: сначала схема тенанта (если есть), затем public.
     * Это позволяет обращаться к tenant-таблицам без явного указания схемы,
     * а к global-таблицам (users, tenants) — через public.
     */
    private void setSearchPath(Connection connection) throws SQLException {
        String schema = TenantContext.get();
        String searchPath;

        if ("public".equals(schema) || schema == null) {
            searchPath = "public";
        } else {
            // Защита от SQL injection: схема должна быть валидным идентификатором
            validateSchemaName(schema);
            searchPath = schema + ", public";
        }

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO " + searchPath);
            log.trace("search_path set to: {}", searchPath);
        }
    }

    private void validateSchemaName(String schema) {
        if (!schema.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(
                "Invalid tenant schema name (SQL injection attempt?): " + schema
            );
        }
    }
}
