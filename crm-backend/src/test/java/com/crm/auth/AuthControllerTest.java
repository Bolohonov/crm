package com.crm.auth;

import com.crm.auth.dto.AuthResponse;
import com.crm.auth.dto.LoginRequest;
import com.crm.auth.dto.RegisterRequest;
import com.crm.auth.service.AuthService;
import com.crm.common.exception.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Юнит-тесты AuthController.
 * Используется @WebMvcTest — поднимается только MVC-слой, без БД.
 */
@WebMvcTest(controllers = com.crm.auth.controller.AuthController.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AuthService authService;

    // ── Вспомогательный builder ────────────────────────────────────
    private AuthResponse fakeAuthResponse() {
        return AuthResponse.builder()
            .accessToken("eyJ.fake.token")
            .refreshToken("refresh.token")
            .expiresIn(900L)
            .userId(UUID.randomUUID())
            .email("user@test.com")
            .firstName("Иван")
            .lastName("Петров")
            .userType("REGULAR")
            .build();
    }

    // ── POST /auth/login ──────────────────────────────────────────
    @Nested
    @DisplayName("POST /auth/login")
    class Login {

        @Test
        @DisplayName("успешный вход — 200 с токенами")
        void login_success() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                .thenReturn(fakeAuthResponse());

            LoginRequest req = new LoginRequest();
            req.setEmail("user@test.com");
            req.setPassword("password123");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
        }

        @Test
        @DisplayName("неверный пароль — 401")
        void login_wrongPassword() throws Exception {
            when(authService.login(any(LoginRequest.class)))
                .thenThrow(AppException.unauthorized("Неверный email или пароль"));

            LoginRequest req = new LoginRequest();
            req.setEmail("user@test.com");
            req.setPassword("wrong");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("пустой email — 400 validation error")
        void login_emptyEmail() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("");
            req.setPassword("password123");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("невалидный email — 400")
        void login_invalidEmail() throws Exception {
            LoginRequest req = new LoginRequest();
            req.setEmail("not-an-email");
            req.setPassword("password123");

            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ── POST /auth/register ───────────────────────────────────────
    @Nested
    @DisplayName("POST /auth/register")
    class Register {

        @Test
        @DisplayName("успешная регистрация — 201")
        void register_success() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                .thenReturn(com.crm.auth.dto.RegisterResponse.builder()
                    .userId(UUID.randomUUID())
                    .email("newuser@test.com")
                    .message("Письмо с подтверждением отправлено")
                    .build());

            RegisterRequest req = new RegisterRequest();
            req.setEmail("newuser@test.com");
            req.setPassword("SecurePass123!");
            req.setFirstName("Анна");
            req.setLastName("Иванова");
            req.setPhone("+79001234567");
            req.setUserType(com.crm.user.entity.UserType.ADMIN);

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("newuser@test.com"));
        }

        @Test
        @DisplayName("email уже занят — 409")
        void register_emailConflict() throws Exception {
            when(authService.register(any(RegisterRequest.class)))
                .thenThrow(AppException.conflict("EMAIL_EXISTS",
                    "Пользователь с таким email уже зарегистрирован"));

            RegisterRequest req = new RegisterRequest();
            req.setEmail("exists@test.com");
            req.setPassword("Password123!");
            req.setFirstName("Иван");
            req.setLastName("Петров");
            req.setPhone("+79001234567");
            req.setUserType(com.crm.user.entity.UserType.ADMIN);

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EMAIL_EXISTS"));
        }

        @Test
        @DisplayName("слишком короткий пароль — 400")
        void register_shortPassword() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("user@test.com");
            req.setPassword("123");   // < 8 символов
            req.setFirstName("Иван");
            req.setLastName("Петров");
            req.setPhone("+79001234567");
            req.setUserType(com.crm.user.entity.UserType.ADMIN);

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("отсутствует userType — 400")
        void register_missingUserType() throws Exception {
            RegisterRequest req = new RegisterRequest();
            req.setEmail("user@test.com");
            req.setPassword("SecurePass123!");
            req.setFirstName("Иван");
            req.setLastName("Петров");
            req.setPhone("+79001234567");
            // userType не задан → @NotNull должен сработать

            mockMvc.perform(post("/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }
}
