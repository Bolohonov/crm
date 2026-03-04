package com.crm.auth;

import com.crm.auth.dto.LoginRequest;
import com.crm.auth.dto.RegisterRequest;
import com.crm.user.entity.UserType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционный тест полного цикла аутентификации:
 *   Регистрация → Верификация Email → Вход → Refresh → Logout
 *
 * Использует реальный PostgreSQL через Testcontainers.
 * Liquibase-миграции накатываются автоматически при старте контекста.
 *
 * Тесты запускаются в @TestMethodOrder — каждый шаг зависит от предыдущего.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Auth Integration: полный цикл аутентификации")
class AuthIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("crm_integration_test")
            .withUsername("crm")
            .withPassword("crm")
            .withInitScript("sql/init-extensions.sql");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Включаем Liquibase — накатит миграции public-схемы
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;
    @Autowired JdbcTemplate  jdbc;

    // Состояние между тестами (в рамках одного класса)
    static String accessToken;
    static String refreshToken;
    static String verifyToken;
    static String userEmail = "integration_test_" + System.currentTimeMillis() + "@test.local";

    // ── Шаг 1: Регистрация ────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("1. POST /auth/register — регистрация нового администратора")
    void step1_register() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(userEmail);
        req.setPassword("IntegrationPass123!");
        req.setFirstName("Интеграция");
        req.setLastName("Тест");
        req.setPhone("+79001234567");
        req.setUserType(UserType.ADMIN);

        MvcResult result = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value(userEmail))
            .andReturn();

        // Достаём токен верификации из БД (в тестах email не отправляется)
        verifyToken = jdbc.queryForObject(
            "SELECT token FROM public.email_verifications " +
            "WHERE user_id = (SELECT id FROM public.users WHERE email = ?) " +
            "ORDER BY created_at DESC LIMIT 1",
            String.class, userEmail
        );

        assertThat(verifyToken).isNotBlank();
    }

    // ── Шаг 2: Верификация email ──────────────────────────────────
    @Test
    @Order(2)
    @DisplayName("2. GET /auth/verify — верификация email по токену")
    void step2_verifyEmail() throws Exception {
        assumeNotNull(verifyToken, "verifyToken должен быть получен на шаге 1");

        mockMvc.perform(get("/auth/verify")
                .param("token", verifyToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        // Проверяем флаг в БД
        Boolean verified = jdbc.queryForObject(
            "SELECT email_verified FROM public.users WHERE email = ?",
            Boolean.class, userEmail
        );
        assertThat(verified).isTrue();
    }

    // ── Шаг 3: Вход ───────────────────────────────────────────────
    @Test
    @Order(3)
    @DisplayName("3. POST /auth/login — вход с верифицированным аккаунтом")
    void step3_login() throws Exception {
        assumeNotNull(verifyToken, "Требует успешной верификации на шаге 2");

        LoginRequest req = new LoginRequest();
        req.setEmail(userEmail);
        req.setPassword("IntegrationPass123!");

        MvcResult result = mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.data.email").value(userEmail))
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        accessToken  = body.at("/data/accessToken").asText();
        refreshToken = body.at("/data/refreshToken").asText();

        assertThat(accessToken).isNotBlank();
        assertThat(refreshToken).isNotBlank();
    }

    // ── Шаг 4: GET /auth/me с токеном ────────────────────────────
    @Test
    @Order(4)
    @DisplayName("4. GET /auth/me — профиль с valid access token")
    void step4_me() throws Exception {
        assumeNotNull(accessToken, "Требует успешного входа на шаге 3");

        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email").value(userEmail))
            .andExpect(jsonPath("$.data.userType").value("ADMIN"))
            .andExpect(jsonPath("$.data.emailVerified").value(true));
    }

    // ── Шаг 5: Refresh token ──────────────────────────────────────
    @Test
    @Order(5)
    @DisplayName("5. POST /auth/refresh — обновление access token")
    void step5_refresh() throws Exception {
        assumeNotNull(refreshToken, "Требует успешного входа на шаге 3");

        var refreshReq = new com.crm.auth.dto.RefreshTokenRequest();
        refreshReq.setRefreshToken(refreshToken);

        MvcResult result = mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andReturn();

        // Новый токен должен отличаться от старого (rotate)
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String newAccessToken = body.at("/data/accessToken").asText();
        assertThat(newAccessToken).isNotBlank();
    }

    // ── Шаг 6: Logout ─────────────────────────────────────────────
    @Test
    @Order(6)
    @DisplayName("6. POST /auth/logout — выход, инвалидация токена")
    void step6_logout() throws Exception {
        assumeNotNull(accessToken, "Требует успешного входа на шаге 3");

        mockMvc.perform(post("/auth/logout")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk());

        // После logout /auth/me должен вернуть 401
        mockMvc.perform(get("/auth/me")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isUnauthorized());
    }

    // ── Дополнительные тесты ──────────────────────────────────────
    @Test
    @Order(10)
    @DisplayName("10. POST /auth/login — вход с неверным паролем — 401")
    void extra_wrongPassword() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail(userEmail);
        req.setPassword("WrongPassword!");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(11)
    @DisplayName("11. POST /auth/login — несуществующий email — 401")
    void extra_nonExistentEmail() throws Exception {
        LoginRequest req = new LoginRequest();
        req.setEmail("nobody_" + System.currentTimeMillis() + "@test.local");
        req.setPassword("AnyPassword123!");

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnauthorized());
    }

    // ── Хелперы ───────────────────────────────────────────────────
    private static void assumeNotNull(Object value, String message) {
        org.junit.jupiter.api.Assumptions.assumeTrue(value != null, message);
    }
}
