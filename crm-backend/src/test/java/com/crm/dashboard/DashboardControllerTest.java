package com.crm.dashboard;

import com.crm.common.TestSecurityUtils;
import com.crm.dashboard.controller.DashboardController;
import com.crm.dashboard.dto.*;
import com.crm.dashboard.service.DashboardService;
import com.crm.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DashboardController.class)
@DisplayName("DashboardController")
class DashboardControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean DashboardService dashboardService;

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

    // ── GET /dashboard/stats ──────────────────────────────────────
    @Nested @DisplayName("GET /dashboard/stats")
    class Stats {

        @Test
        @WithMockUser
        @DisplayName("возвращает сводную статистику")
        void stats_success() throws Exception {
            when(dashboardService.getStats()).thenReturn(new DashboardStatsDto(
                new DashboardStatsDto.CustomerStats(60, 5, 12),
                new DashboardStatsDto.OrderStats(
                    80L, new BigDecimal("11400000"),
                    new BigDecimal("1100000"), 8,
                    new BigDecimal("142500")
                ),
                new DashboardStatsDto.TaskStats(50, 10, 3, 7),
                new DashboardStatsDto.ProductStats(25, 22)
            ));

            mockMvc.perform(get("/dashboard/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customers.total").value(60))
                .andExpect(jsonPath("$.data.orders.total").value(80))
                .andExpect(jsonPath("$.data.tasks.overdue").value(10))
                .andExpect(jsonPath("$.data.products.active").value(22));
        }

        @Test
        @DisplayName("без аутентификации — 401")
        void stats_unauthenticated() throws Exception {
            mockMvc.perform(get("/dashboard/stats"))
                .andExpect(status().isUnauthorized());
        }
    }

    // ── GET /dashboard/funnel ─────────────────────────────────────
    @Nested @DisplayName("GET /dashboard/funnel")
    class Funnel {

        @Test
        @WithMockUser(authorities = "ORDER_VIEW")
        @DisplayName("возвращает воронку с 5 этапами")
        void funnel_success() throws Exception {
            when(dashboardService.getFunnel()).thenReturn(List.of(
                new FunnelStageDto("NEW",         "Новый",        "#3b82f6", 18L, new BigDecimal("2500000"), 100, 0),
                new FunnelStageDto("IN_PROGRESS", "В работе",     "#8b5cf6", 22L, new BigDecimal("3800000"), 122,  0),
                new FunnelStageDto("WAITING",     "Ожидает",      "#f59e0b", 12L, new BigDecimal("1900000"),  67, 55),
                new FunnelStageDto("DONE",        "Выполнен",     "#10b981", 20L, new BigDecimal("3200000"), 111, 63),
                new FunnelStageDto("CANCELLED",   "Отменён",      "#ef4444",  8L, new BigDecimal("900000"),   44,  0)
            ));

            mockMvc.perform(get("/dashboard/funnel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].statusCode").value("NEW"))
                .andExpect(jsonPath("$.data[3].statusCode").value("DONE"));
        }

        @Test
        @WithMockUser  // без ORDER_VIEW
        @DisplayName("без права ORDER_VIEW — 403")
        void funnel_forbidden() throws Exception {
            mockMvc.perform(get("/dashboard/funnel"))
                .andExpect(status().isForbidden());
        }
    }

    // ── GET /dashboard/revenue ────────────────────────────────────
    @Nested @DisplayName("GET /dashboard/revenue")
    class Revenue {

        @Test
        @WithMockUser(authorities = "ORDER_VIEW")
        @DisplayName("возвращает выручку за 6 месяцев по умолчанию")
        void revenue_defaultMonths() throws Exception {
            when(dashboardService.getRevenue(6)).thenReturn(List.of(
                new RevenuePointDto("2025-09", "Сен", new BigDecimal("800000"),  15L),
                new RevenuePointDto("2025-10", "Окт", new BigDecimal("950000"),  18L),
                new RevenuePointDto("2025-11", "Ноя", new BigDecimal("1050000"), 20L),
                new RevenuePointDto("2025-12", "Дек", new BigDecimal("880000"),  16L),
                new RevenuePointDto("2026-01", "Янв", new BigDecimal("1020000"), 19L),
                new RevenuePointDto("2026-02", "Фев", new BigDecimal("1100000"), 21L)
            ));

            mockMvc.perform(get("/dashboard/revenue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(6))
                .andExpect(jsonPath("$.data[5].month").value("2026-02"))
                .andExpect(jsonPath("$.data[5].label").value("Фев"));
        }

        @Test
        @WithMockUser(authorities = "ORDER_VIEW")
        @DisplayName("months=3 передаётся в сервис")
        void revenue_customMonths() throws Exception {
            when(dashboardService.getRevenue(3)).thenReturn(List.of());

            mockMvc.perform(get("/dashboard/revenue").param("months", "3"))
                .andExpect(status().isOk());

            verify(dashboardService).getRevenue(3);
        }

        @Test
        @WithMockUser(authorities = "ORDER_VIEW")
        @DisplayName("months=100 зажимается до 24")
        void revenue_monthsClamped() throws Exception {
            when(dashboardService.getRevenue(24)).thenReturn(List.of());

            mockMvc.perform(get("/dashboard/revenue").param("months", "100"))
                .andExpect(status().isOk());

            verify(dashboardService).getRevenue(24);
        }
    }

    // ── GET /dashboard/tasks/overdue ──────────────────────────────
    @Nested @DisplayName("GET /dashboard/tasks/overdue")
    class OverdueTasks {

        @Test
        @WithMockUser(authorities = "TASK_VIEW")
        @DisplayName("возвращает просроченные задачи")
        void overdue_success() throws Exception {
            when(dashboardService.getOverdueTasks(5)).thenReturn(List.of(
                new OverdueTaskDto(UUID.randomUUID().toString(), "Критическая задача",
                    "CRITICAL", 5, "ООО Клиент", "Иванов И."),
                new OverdueTaskDto(UUID.randomUUID().toString(), "Важная задача",
                    "HIGH", 2, "ИП Петров", "Козлов А.")
            ));

            mockMvc.perform(get("/dashboard/tasks/overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].priority").value("CRITICAL"))
                .andExpect(jsonPath("$.data[0].daysOverdue").value(5));
        }

        @Test
        @WithMockUser  // без TASK_VIEW
        @DisplayName("без права TASK_VIEW — 403")
        void overdue_forbidden() throws Exception {
            mockMvc.perform(get("/dashboard/tasks/overdue"))
                .andExpect(status().isForbidden());
        }
    }

    // ── GET /dashboard/activity ───────────────────────────────────
    @Nested @DisplayName("GET /dashboard/activity")
    class Activity {

        @Test
        @WithMockUser
        @DisplayName("возвращает ленту активности")
        void activity_success() throws Exception {
            when(dashboardService.getRecentActivity(8)).thenReturn(List.of(
                new RecentActivityDto(UUID.randomUUID().toString(),
                    "ORDER_CREATED", "Создан заказ: Лицензия Office (₽75К)",
                    UUID.randomUUID().toString(), "ORDER",
                    Instant.now(), "Иванов И."),
                new RecentActivityDto(UUID.randomUUID().toString(),
                    "TASK_COMPLETED", "Задача выполнена: Позвонить клиенту",
                    UUID.randomUUID().toString(), "TASK",
                    Instant.now().minusSeconds(300), "Козлов А.")
            ));

            mockMvc.perform(get("/dashboard/activity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].type").value("ORDER_CREATED"))
                .andExpect(jsonPath("$.data[1].type").value("TASK_COMPLETED"));
        }
    }

    // ── GET /dashboard/customers/top ──────────────────────────────
    @Nested @DisplayName("GET /dashboard/customers/top")
    class TopCustomers {

        @Test
        @WithMockUser(authorities = "CUSTOMER_VIEW")
        @DisplayName("возвращает топ-5 клиентов по выручке")
        void topCustomers_success() throws Exception {
            when(dashboardService.getTopCustomers(5)).thenReturn(List.of(
                new TopCustomerDto(UUID.randomUUID().toString(),
                    "ФГУП «Гос. Технологии»", "LEGAL", 12L, new BigDecimal("1160000")),
                new TopCustomerDto(UUID.randomUUID().toString(),
                    "ООО «АгроПром»", "LEGAL", 8L, new BigDecimal("980000")),
                new TopCustomerDto(UUID.randomUUID().toString(),
                    "АО «ЦифраГрупп»", "LEGAL", 6L, new BigDecimal("720000"))
            ));

            mockMvc.perform(get("/dashboard/customers/top"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].customerName")
                    .value("ФГУП «Гос. Технологии»"))
                .andExpect(jsonPath("$.data[0].orderCount").value(12));
        }

        @Test
        @WithMockUser  // без CUSTOMER_VIEW
        @DisplayName("без права CUSTOMER_VIEW — 403")
        void topCustomers_forbidden() throws Exception {
            mockMvc.perform(get("/dashboard/customers/top"))
                .andExpect(status().isForbidden());
        }
    }
}
