package com.crm.customer;

import com.crm.auth.service.JwtService;
import com.crm.common.TestSecurityUtils;
import com.crm.customer.controller.CustomerController;
import com.crm.customer.dto.CustomerDto;
import com.crm.customer.service.CustomerService;
import com.crm.tenant.TenantContext;
import com.crm.user.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

@WebMvcTest(CustomerController.class)
@DisplayName("CustomerController")
class CustomerControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean CustomerService customerService;
    @MockBean JwtService jwtService;

    private MockedStatic<TenantContext> tenantContextMock;

    @BeforeEach
    void setupTenantContext() {
        tenantContextMock = Mockito.mockStatic(TenantContext.class);
        tenantContextMock.when(TenantContext::get)
            .thenReturn(TestSecurityUtils.testTenant());
        tenantContextMock.when(TenantContext::getCurrentSchema)
            .thenReturn(TestSecurityUtils.TEST_SCHEMA);
    }

    @AfterEach
    void closeTenantContext() {
        tenantContextMock.close();
    }

    // ── Тестовые данные ───────────────────────────────────────────
    private CustomerDto.CustomerResponse fakeCustomer() {
        return CustomerDto.CustomerResponse.builder()
            .id(UUID.randomUUID())
            .type("LEGAL")
            .companyName("ООО Тест")
            .email("test@company.ru")
            .isActive(true)
            .build();
    }

    private CustomerDto.PageResponse fakePage(int count) {
        List<CustomerDto.CustomerResponse> items =
            java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> fakeCustomer())
                .toList();

        return CustomerDto.PageResponse.builder()
            .content(items)
            .page(0).size(20)
            .totalElements(count)
            .totalPages((int) Math.ceil(count / 20.0))
            .build();
    }

    // ── GET /customers ────────────────────────────────────────────
    @Nested
    @DisplayName("GET /customers")
    class ListCustomers {

        @Test
        @WithMockUser(authorities = "CUSTOMER_VIEW")
        @DisplayName("возвращает список клиентов — 200")
        void list_success() throws Exception {
            when(customerService.search(any())).thenReturn(fakePage(3));

            mockMvc.perform(get("/customers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @WithMockUser(authorities = "CUSTOMER_VIEW")
        @DisplayName("фильтрация по типу — передаётся в сервис")
        void list_withTypeFilter() throws Exception {
            when(customerService.search(any())).thenReturn(fakePage(1));

            mockMvc.perform(get("/customers")
                    .param("type", "LEGAL")
                    .param("page", "0")
                    .param("size", "10"))
                .andExpect(status().isOk());

            verify(customerService).search(argThat(req ->
                "LEGAL".equals(req.getType()) &&
                req.getPage() == 0 &&
                req.getSize() == 10
            ));
        }

        @Test
        @WithMockUser // без CUSTOMER_VIEW
        @DisplayName("без права CUSTOMER_VIEW — 403")
        void list_forbidden() throws Exception {
            mockMvc.perform(get("/customers"))
                .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("без аутентификации — 401")
        void list_unauthenticated() throws Exception {
            mockMvc.perform(get("/customers"))
                .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /customers/{id} ───────────────────────────────────────
    @Nested
    @DisplayName("GET /customers/{id}")
    class GetCustomer {

        @Test
        @WithMockUser(authorities = "CUSTOMER_VIEW")
        @DisplayName("существующий клиент — 200")
        void getById_success() throws Exception {
            UUID id = UUID.randomUUID();
            when(customerService.getById(id)).thenReturn(fakeCustomer());

            mockMvc.perform(get("/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("ООО Тест"));
        }

        @Test
        @WithMockUser(authorities = "CUSTOMER_VIEW")
        @DisplayName("несуществующий клиент — 404")
        void getById_notFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(customerService.getById(id))
                .thenThrow(com.crm.common.exception.AppException.notFound("Customer"));

            mockMvc.perform(get("/customers/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
        }
    }

    // ── POST /customers ───────────────────────────────────────────
    @Nested
    @DisplayName("POST /customers")
    class CreateCustomer {

        @Test
        @WithMockUser(authorities = {"CUSTOMER_CREATE", "CUSTOMER_VIEW"})
        @DisplayName("создание юр. лица — 201")
        void create_legal_success() throws Exception {
            when(customerService.create(any(), any(User.class)))
                .thenReturn(fakeCustomer());

            var req = new CustomerDto.CreateRequest();
            req.setType("LEGAL");
            req.setCompanyName("ООО Новый Клиент");
            req.setEmail("new@company.ru");
            req.setPhone("+79001234567");

            mockMvc.perform(post("/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(authorities = "CUSTOMER_CREATE")
        @DisplayName("пустое имя компании — 400")
        void create_missingCompanyName() throws Exception {
            var req = new CustomerDto.CreateRequest();
            req.setType("LEGAL");
            req.setEmail("new@company.ru");
            // companyName не задан

            mockMvc.perform(post("/customers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ── DELETE /customers/{id} ────────────────────────────────────
    @Nested
    @DisplayName("DELETE /customers/{id}")
    class DeleteCustomer {

        @Test
        @WithMockUser(authorities = "CUSTOMER_DELETE")
        @DisplayName("удаление существующего клиента — 200")
        void delete_success() throws Exception {
            UUID id = UUID.randomUUID();
            doNothing().when(customerService).delete(id);

            mockMvc.perform(delete("/customers/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @WithMockUser(authorities = "CUSTOMER_VIEW") // нет CUSTOMER_DELETE
        @DisplayName("без права CUSTOMER_DELETE — 403")
        void delete_forbidden() throws Exception {
            mockMvc.perform(delete("/customers/{id}", UUID.randomUUID()))
                .andExpect(status().isForbidden());
        }
    }
}
