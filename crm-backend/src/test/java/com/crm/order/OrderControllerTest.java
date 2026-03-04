package com.crm.order;

import com.crm.common.TestSecurityUtils;
import com.crm.common.exception.AppException;
import com.crm.order.controller.OrderController;
import com.crm.order.dto.OrderDto;
import com.crm.order.service.OrderService;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@DisplayName("OrderController")
class OrderControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean OrderService orderService;

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

    // ── Builders ──────────────────────────────────────────────────
    private OrderDto.OrderResponse order(UUID id, String statusCode, BigDecimal amount) {
        return OrderDto.OrderResponse.builder()
            .id(id)
            .customerId(UUID.randomUUID())
            .customerName("ООО Клиент")
            .statusCode(statusCode)
            .statusName("Новый")
            .statusColor("#3b82f6")
            .totalAmount(amount)
            .items(List.of())
            .createdAt(Instant.now())
            .build();
    }

    private OrderDto.PageResponse page(List<OrderDto.OrderResponse> items) {
        return OrderDto.PageResponse.builder()
            .content(items)
            .page(0).size(20)
            .totalElements(items.size())
            .totalPages(1)
            .build();
    }

    // ── GET /orders ───────────────────────────────────────────────
    @Nested @DisplayName("GET /orders")
    class ListOrders {

        @Test
        @WithMockUser
        @DisplayName("возвращает список заказов — 200")
        void list_success() throws Exception {
            var items = List.of(
                order(UUID.randomUUID(), "NEW",       new BigDecimal("150000")),
                order(UUID.randomUUID(), "IN_PROGRESS", new BigDecimal("250000"))
            );
            when(orderService.list(any())).thenReturn(page(items));

            mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].statusCode").value("NEW"));
        }

        @Test
        @WithMockUser
        @DisplayName("фильтрация по customerId — передаётся в сервис")
        void list_filterByCustomer() throws Exception {
            UUID customerId = UUID.randomUUID();
            when(orderService.list(any())).thenReturn(page(List.of()));

            mockMvc.perform(get("/orders")
                    .param("customerId", customerId.toString()))
                .andExpect(status().isOk());

            verify(orderService).list(argThat(req ->
                customerId.equals(req.getCustomerId())
            ));
        }

        @Test
        @WithMockUser
        @DisplayName("фильтрация по statusId и authorId")
        void list_filterByStatusAndAuthor() throws Exception {
            UUID statusId = UUID.randomUUID();
            UUID authorId = UUID.randomUUID();
            when(orderService.list(any())).thenReturn(page(List.of()));

            mockMvc.perform(get("/orders")
                    .param("statusId", statusId.toString())
                    .param("authorId", authorId.toString())
                    .param("page", "1")
                    .param("size", "10"))
                .andExpect(status().isOk());

            verify(orderService).list(argThat(req ->
                statusId.equals(req.getStatusId()) &&
                authorId.equals(req.getAuthorId()) &&
                req.getPage() == 1 &&
                req.getSize() == 10
            ));
        }

        @Test
        @DisplayName("без аутентификации — 401")
        void list_unauthenticated() throws Exception {
            mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /orders/stats ─────────────────────────────────────────
    @Nested @DisplayName("GET /orders/stats")
    class OrderStats {

        @Test
        @WithMockUser
        @DisplayName("возвращает статистику по статусам")
        void stats_success() throws Exception {
            var stats = OrderDto.StatsResponse.builder()
                .totalOrders(80L)
                .build();
            when(orderService.getStats()).thenReturn(stats);

            mockMvc.perform(get("/orders/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalOrders").value(80));
        }
    }

    // ── GET /orders/{id} ──────────────────────────────────────────
    @Nested @DisplayName("GET /orders/{id}")
    class GetOrder {

        @Test
        @WithMockUser
        @DisplayName("существующий заказ — 200 со всеми полями")
        void getById_success() throws Exception {
            UUID id = UUID.randomUUID();
            when(orderService.getById(id))
                .thenReturn(order(id, "NEW", new BigDecimal("480000")));

            mockMvc.perform(get("/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()))
                .andExpect(jsonPath("$.data.totalAmount").value(480000));
        }

        @Test
        @WithMockUser
        @DisplayName("несуществующий заказ — 404")
        void getById_notFound() throws Exception {
            UUID id = UUID.randomUUID();
            when(orderService.getById(id))
                .thenThrow(AppException.notFound("Order"));

            mockMvc.perform(get("/orders/{id}", id))
                .andExpect(status().isNotFound());
        }
    }

    // ── POST /orders ──────────────────────────────────────────────
    @Nested @DisplayName("POST /orders")
    class CreateOrder {

        @Test
        @WithMockUser
        @DisplayName("создание заказа с позициями — 201")
        void create_success() throws Exception {
            UUID newId = UUID.randomUUID();
            when(orderService.create(any(), any()))
                .thenReturn(order(newId, "NEW", new BigDecimal("75000")));

            var item = new OrderDto.ItemRequest();
            item.setProductId(UUID.randomUUID());
            item.setQuantity(new BigDecimal("2"));
            item.setPrice(new BigDecimal("37500"));

            var req = new OrderDto.CreateRequest();
            req.setCustomerId(UUID.randomUUID());
            req.setStatusId(UUID.randomUUID());
            req.setItems(List.of(item));

            mockMvc.perform(post("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(newId.toString()));
        }

        @Test
        @WithMockUser
        @DisplayName("заказ без customerId — 400")
        void create_missingCustomer() throws Exception {
            var req = new OrderDto.CreateRequest();
            // customerId не задан — @NotNull

            mockMvc.perform(post("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        }
    }

    // ── PATCH /orders/{id}/status ─────────────────────────────────
    @Nested @DisplayName("PATCH /orders/{id}/status")
    class ChangeStatus {

        @Test
        @WithMockUser
        @DisplayName("смена статуса — 200")
        void changeStatus_success() throws Exception {
            UUID orderId   = UUID.randomUUID();
            UUID newStatus = UUID.randomUUID();
            doNothing().when(orderService).changeStatus(orderId, newStatus);

            mockMvc.perform(patch("/orders/{id}/status", orderId)
                    .param("statusId", newStatus.toString()))
                .andExpect(status().isOk());

            verify(orderService).changeStatus(orderId, newStatus);
        }

        @Test
        @WithMockUser
        @DisplayName("заказ не найден — 404")
        void changeStatus_notFound() throws Exception {
            UUID orderId   = UUID.randomUUID();
            UUID statusId  = UUID.randomUUID();
            doThrow(AppException.notFound("Order"))
                .when(orderService).changeStatus(orderId, statusId);

            mockMvc.perform(patch("/orders/{id}/status", orderId)
                    .param("statusId", statusId.toString()))
                .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /orders/{id} ───────────────────────────────────────
    @Nested @DisplayName("DELETE /orders/{id}")
    class DeleteOrder {

        @Test
        @WithMockUser
        @DisplayName("успешное удаление — 200")
        void delete_success() throws Exception {
            UUID id = UUID.randomUUID();
            doNothing().when(orderService).delete(id);

            mockMvc.perform(delete("/orders/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

            verify(orderService).delete(id);
        }
    }
}
