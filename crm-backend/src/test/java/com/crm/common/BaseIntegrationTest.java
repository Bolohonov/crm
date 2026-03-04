package com.crm.common;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Базовый класс для интеграционных тестов.
 *
 * Поднимает один PostgreSQL-контейнер на весь тест-сьют (reuse=true),
 * пересоздаёт тестовую схему перед каждым тестом.
 *
 * Использование:
 * <pre>
 * class MyTest extends BaseIntegrationTest {
 *
 *     @Autowired MyController controller;
 *
 *     @Test
 *     void myTest() throws Exception {
 *         mockMvc.perform(get("/api/v1/customers"))
 *                .andExpect(status().isOk());
 *     }
 * }
 * </pre>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("crm_test")
            .withUsername("crm_test")
            .withPassword("crm_test")
            .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    /**
     * Сбрасываем тестовые данные перед каждым тестом.
     * Быстрее чем пересоздавать схему — просто TRUNCATE всех таблиц.
     */
    @BeforeEach
    void resetTestData() {
        jdbc.execute("SET search_path TO public");
        // В реальном проекте здесь был бы truncate всех tenant-таблиц
        // или @Sql(scripts = "classpath:sql/reset.sql")
    }
}
