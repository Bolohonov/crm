package com.crm.common.config;

import com.crm.tenant.TenantAwareDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Конфигурация DataSource.
 *
 * Архитектура:
 *   HikariDataSource (физический пул) → TenantAwareDataSource (обёртка)
 *
 * TenantAwareDataSource при каждом getConnection() устанавливает
 * SET search_path TO <tenant_schema>, public
 * на основе TenantContext (ThreadLocal).
 */
@Configuration
@RequiredArgsConstructor
public class DataSourceConfig {

    /**
     * Физический пул соединений HikariCP.
     * Все соединения идут в одну БД — схема переключается через search_path.
     */
    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public HikariConfig hikariConfig(DataSourceProperties dataSourceProperties) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dataSourceProperties.getUrl());
        config.setUsername(dataSourceProperties.getUsername());
        config.setPassword(dataSourceProperties.getPassword());
        config.setDriverClassName(dataSourceProperties.getDriverClassName());
        return config;
    }

    @Bean
    public HikariDataSource hikariDataSource(HikariConfig hikariConfig) {
        return new HikariDataSource(hikariConfig);
    }

    /**
     * Основной DataSource — TenantAwareDataSource поверх Hikari.
     * @Primary чтобы Spring использовал его везде где инжектируется DataSource.
     */
    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource hikariDataSource) {
        TenantAwareDataSource tenantDataSource = new TenantAwareDataSource();

        // AbstractRoutingDataSource требует targetDataSources и defaultTargetDataSource
        tenantDataSource.setTargetDataSources(Map.of("default", hikariDataSource));
        tenantDataSource.setDefaultTargetDataSource(hikariDataSource);
        tenantDataSource.afterPropertiesSet();

        return tenantDataSource;
    }

    /**
     * JdbcTemplate использует основной DataSource (с tenant routing).
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
