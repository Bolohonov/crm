package com.crm.user;

import com.crm.common.TestSecurityUtils;
import com.crm.common.exception.AppException;
import com.crm.tenant.TenantContext;
import com.crm.user.controller.UserController;
import com.crm.user.dto.UserDto;
import com.crm.user.entity.UserStatus;
import com.crm.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@DisplayName("UserController")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean UserService userService;

    private MockedStatic<TenantContext> tenantMock;

    @BeforeEach
    void setUp() {
        tenantMock = Mockito.mockStatic(TenantContext.class);
        tenantMock.when(TenantContext::get).thenReturn(TestSecurityUtils.testTenant());
    }

    @AfterEach
    void tearDown() { tenantMock.close(); }

    private UserDto.UserResponse fakeUser(UUID id) {
        return UserDto.UserResponse.builder()
            .id(id)
            .email("user_" + id + "@test.local")
            .firstName("Тест")
            .lastName("Пользователь")
            .userType("REGULAR")
            .status("ACTIVE")
            .emailVerified(true)
            .roles(List.of())
            .build();
    }

    // ── GET /users ────────────────────────────────────────────────
    @Nested @DisplayName("GET /users")
    class ListUsers {

        @Test
        @WithMockUser(authorities = "USER_VIEW")
        @DisplayName("возвращает список пользователей")
        void list_success() throws Exception {
            when(userService.list(anyInt(), anyInt(), any()))
                .thenReturn(UserDto.PageResponse.builder()
                    .content(List.of(fakeUser(UUID.randomUUID()), fakeUser(UUID.randomUUID())))
                    .page(0).size(20).totalElements(2).totalPages(1)
                    .build());

            mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        }

        @Test
        @WithMockUser(authorities = "USER_VIEW")
        @DisplayName("поиск по имени передаётся в сервис")
        void list_withSearch() throws Exception {
            when(userService.list(0, 20, "Иван"))
                .thenReturn(UserDto.PageResponse.builder()
                    .content(List.of()).page(0).size(20)
                    .totalElements(0).totalPages(0).build());

            mockMvc.perform(get("/users").param("q", "Иван"))
                .andExpect(status().isOk());

            verify(userService).list(0, 20, "Иван");
        }

        @Test
        @WithMockUser // без USER_VIEW
        @DisplayName("без права USER_VIEW — 403")
        void list_forbidden() throws Exception {
            mockMvc.perform(get("/users"))
                .andExpect(status().isForbidden());
        }
    }

    // ── PATCH /users/{id}/status ──────────────────────────────────
    @Nested @DisplayName("PATCH /users/{id}/status")
    class SetStatus {

        @Test
        @WithMockUser(username = "admin@test.local", authorities = "USER_MANAGE")
        @DisplayName("блокировка пользователя — 200")
        void block_success() throws Exception {
            UUID targetId = UUID.randomUUID();
            doNothing().when(userService).setStatus(any(), any(), any());

            var req = new UserDto.SetStatusRequest();
            req.setStatus(UserStatus.BLOCKED);

            mockMvc.perform(patch("/users/{id}/status", targetId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(authorities = "USER_MANAGE")
        @DisplayName("попытка заблокировать себя — 400")
        void block_self() throws Exception {
            UUID selfId = UUID.randomUUID();
            doThrow(AppException.badRequest("SELF_STATUS",
                "Нельзя изменить собственный статус"))
                .when(userService).setStatus(any(), any(), any());

            var req = new UserDto.SetStatusRequest();
            req.setStatus(UserStatus.BLOCKED);

            mockMvc.perform(patch("/users/{id}/status", selfId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SELF_STATUS"));
        }

        @Test
        @WithMockUser // без USER_MANAGE
        @DisplayName("без права USER_MANAGE — 403")
        void block_forbidden() throws Exception {
            var req = new UserDto.SetStatusRequest();
            req.setStatus(UserStatus.BLOCKED);

            mockMvc.perform(patch("/users/{id}/status", UUID.randomUUID())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }
    }

    // ── PATCH /users/me/password ──────────────────────────────────
    @Nested @DisplayName("PATCH /users/me/password")
    class SelfChangePassword {

        @Test
        @WithMockUser
        @DisplayName("успешная смена пароля — 200")
        void changePassword_success() throws Exception {
            doNothing().when(userService)
                .selfChangePassword(any(), anyString(), anyString());

            var req = new UserDto.SelfChangePasswordRequest();
            req.setCurrentPassword("OldPass123!");
            req.setNewPassword("NewPass456!");

            mockMvc.perform(patch("/users/me/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser
        @DisplayName("неверный текущий пароль — 400")
        void changePassword_wrongCurrent() throws Exception {
            doThrow(AppException.badRequest("WRONG_PASSWORD", "Текущий пароль неверен"))
                .when(userService).selfChangePassword(any(), anyString(), anyString());

            var req = new UserDto.SelfChangePasswordRequest();
            req.setCurrentPassword("wrong");
            req.setNewPassword("NewPass456!");

            mockMvc.perform(patch("/users/me/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WRONG_PASSWORD"));
        }

        @Test
        @WithMockUser
        @DisplayName("новый пароль менее 8 символов — 400 (validation)")
        void changePassword_tooShort() throws Exception {
            var req = new UserDto.SelfChangePasswordRequest();
            req.setCurrentPassword("OldPass123!");
            req.setNewPassword("123");   // слишком короткий

            mockMvc.perform(patch("/users/me/password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }
}
