package com.crm.status;

import com.crm.common.TestSecurityUtils;
import com.crm.common.exception.AppException;
import com.crm.status.controller.StatusController;
import com.crm.status.dto.StatusDto;
import com.crm.status.service.StatusService;
import com.crm.tenant.TenantContext;
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

@WebMvcTest(StatusController.class)
@DisplayName("StatusController")
class StatusControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean StatusService statusService;

    private MockedStatic<TenantContext> tenantMock;

    @BeforeEach
    void setUp() {
        tenantMock = Mockito.mockStatic(TenantContext.class);
        tenantMock.when(TenantContext::get).thenReturn(TestSecurityUtils.testTenant());
        tenantMock.when(TenantContext::getCurrentSchema)
                  .thenReturn(TestSecurityUtils.TEST_SCHEMA);
    }

    @AfterEach
    void tearDown() { tenantMock.close(); }

    private StatusDto.StatusResponse status(String code, String name, boolean system) {
        return StatusDto.StatusResponse.builder()
            .id(UUID.randomUUID())
            .code(code).name(name)
            .color("#3b82f6")
            .sortOrder(1)
            .isFinal(false)
            .isSystem(system)
            .build();
    }

    // ── GET /statuses/orders ──────────────────────────────────────
    @Nested @DisplayName("GET /statuses/{entity}")
    class ListStatuses {

        @Test
        @WithMockUser
        @DisplayName("возвращает список статусов заказов")
        void listOrders() throws Exception {
            when(statusService.list("orders")).thenReturn(List.of(
                status("NEW", "Новый", true),
                status("DONE", "Выполнен", true),
                status("CUSTOM", "Согласование", false)
            ));

            mockMvc.perform(get("/statuses/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(3));
        }

        @Test
        @WithMockUser
        @DisplayName("неверный тип сущности — 400")
        void invalidEntity() throws Exception {
            when(statusService.list("invoices"))
                .thenThrow(AppException.badRequest("INVALID_ENTITY_TYPE",
                    "Неверный тип: invoices"));

            mockMvc.perform(get("/statuses/invoices"))
                .andExpect(status().isBadRequest());
        }
    }

    // ── POST /statuses/orders ─────────────────────────────────────
    @Nested @DisplayName("POST /statuses/{entity}")
    class CreateStatus {

        @Test
        @WithMockUser(authorities = "ROLE_MANAGE")
        @DisplayName("создаёт пользовательский статус — 201")
        void create_success() throws Exception {
            var created = status("APPROVAL", "На согласовании", false);
            when(statusService.create(eq("orders"), any())).thenReturn(created);

            var req = new StatusDto.CreateRequest();
            req.setCode("APPROVAL");
            req.setName("На согласовании");
            req.setColor("#8b5cf6");

            mockMvc.perform(post("/statuses/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("APPROVAL"));
        }

        @Test
        @WithMockUser(authorities = "ROLE_MANAGE")
        @DisplayName("код с маленькими буквами — 400")
        void create_lowercaseCode() throws Exception {
            var req = new StatusDto.CreateRequest();
            req.setCode("approval");   // должны быть только заглавные
            req.setName("На согласовании");

            mockMvc.perform(post("/statuses/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(authorities = "ROLE_MANAGE")
        @DisplayName("дублирующийся код — 409")
        void create_duplicateCode() throws Exception {
            when(statusService.create(any(), any()))
                .thenThrow(AppException.conflict("STATUS_CODE_EXISTS",
                    "Статус с кодом уже существует"));

            var req = new StatusDto.CreateRequest();
            req.setCode("NEW");
            req.setName("Ещё один новый");

            mockMvc.perform(post("/statuses/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
        }

        @Test
        @WithMockUser // без ROLE_MANAGE
        @DisplayName("без права ROLE_MANAGE — 403")
        void create_forbidden() throws Exception {
            var req = new StatusDto.CreateRequest();
            req.setCode("TEST");
            req.setName("Тест");

            mockMvc.perform(post("/statuses/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
        }
    }

    // ── DELETE /statuses/orders/{id} ──────────────────────────────
    @Nested @DisplayName("DELETE /statuses/{entity}/{id}")
    class DeleteStatus {

        @Test
        @WithMockUser(authorities = "ROLE_MANAGE")
        @DisplayName("успешное удаление пользовательского статуса")
        void delete_success() throws Exception {
            UUID id = UUID.randomUUID();
            doNothing().when(statusService).delete("orders", id);

            mockMvc.perform(delete("/statuses/orders/{id}", id))
                .andExpect(status().isOk());
        }

        @Test
        @WithMockUser(authorities = "ROLE_MANAGE")
        @DisplayName("попытка удалить системный статус — 400")
        void delete_systemStatus() throws Exception {
            UUID id = UUID.randomUUID();
            doThrow(AppException.badRequest("SYSTEM_STATUS",
                "Системные статусы нельзя удалять"))
                .when(statusService).delete("orders", id);

            mockMvc.perform(delete("/statuses/orders/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SYSTEM_STATUS"));
        }

        @Test
        @WithMockUser(authorities = "ROLE_MANAGE")
        @DisplayName("статус используется в заказах — 409")
        void delete_statusInUse() throws Exception {
            UUID id = UUID.randomUUID();
            doThrow(AppException.conflict("STATUS_IN_USE",
                "Статус используется в 5 записях"))
                .when(statusService).delete("orders", id);

            mockMvc.perform(delete("/statuses/orders/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STATUS_IN_USE"));
        }
    }

    // ── PUT /statuses/orders/reorder ──────────────────────────────
    @Nested @DisplayName("PUT /statuses/{entity}/reorder")
    class ReorderStatuses {

        @Test
        @WithMockUser(authorities = "ROLE_MANAGE")
        @DisplayName("изменяет порядок статусов — 200")
        void reorder_success() throws Exception {
            List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
            doNothing().when(statusService).reorder("orders", ids);

            mockMvc.perform(put("/statuses/orders/reorder")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk());

            verify(statusService).reorder(eq("orders"), eq(ids));
        }
    }
}
