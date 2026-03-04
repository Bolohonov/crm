package com.crm.status;

import com.crm.common.TestSecurityUtils;
import com.crm.common.exception.AppException;
import com.crm.status.dto.StatusDto;
import com.crm.status.service.StatusService;
import com.crm.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Чистый юнит-тест StatusService.
 * Нет Spring-контекста, нет БД — только Mockito.
 *
 * Проверяет бизнес-логику:
 * - Маппинг entityType → таблица
 * - Защита системных статусов
 * - Проверка использования перед удалением
 * - Уникальность кода
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("StatusService (unit)")
class StatusServiceTest {

    @Mock JdbcTemplate jdbc;

    @InjectMocks StatusService statusService;

    private MockedStatic<TenantContext> tenantMock;

    @BeforeEach
    void setUp() {
        tenantMock = mockStatic(TenantContext.class);
        tenantMock.when(TenantContext::get)
                  .thenReturn(TestSecurityUtils.testTenant());
        tenantMock.when(TenantContext::getCurrentSchema)
                  .thenReturn(TestSecurityUtils.TEST_SCHEMA);
    }

    @AfterEach
    void tearDown() { tenantMock.close(); }

    // ── resolveTable ──────────────────────────────────────────────
    @Nested @DisplayName("resolveTable")
    class ResolveTable {

        @Test
        @DisplayName("orders → order_statuses")
        void orders_mapsToOrderStatuses() {
            when(jdbc.queryForList(anyString()))
                .thenReturn(List.of());

            assertThatCode(() -> statusService.list("orders"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("tasks → task_statuses")
        void tasks_mapsToTaskStatuses() {
            when(jdbc.queryForList(anyString()))
                .thenReturn(List.of());

            assertThatCode(() -> statusService.list("tasks"))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("неверный тип — AppException 400")
        void unknownType_throws400() {
            assertThatThrownBy(() -> statusService.list("invoices"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("invoices");
        }

        @Test
        @DisplayName("null тип — AppException 400")
        void nullType_throws400() {
            assertThatThrownBy(() -> statusService.list(null))
                .isInstanceOf(AppException.class);
        }
    }

    // ── create ────────────────────────────────────────────────────
    @Nested @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("дублирующийся код — конфликт 409")
        void duplicateCode_throwsConflict() {
            // Имитируем что код уже существует
            when(jdbc.queryForObject(
                contains("COUNT(*)"),
                eq(Integer.class),
                eq("DUPLICATE"))
            ).thenReturn(1);

            StatusDto.CreateRequest req = new StatusDto.CreateRequest();
            req.setCode("DUPLICATE");
            req.setName("Дублирующийся статус");

            assertThatThrownBy(() -> statusService.create("orders", req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("DUPLICATE");
        }

        @Test
        @DisplayName("уникальный код — успешное создание")
        void uniqueCode_success() {
            when(jdbc.queryForObject(
                contains("COUNT(*)"),
                eq(Integer.class),
                any())
            ).thenReturn(0);

            // Мок для INSERT и SELECT после создания
            UUID newId = UUID.randomUUID();
            when(jdbc.update(contains("INSERT"), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", newId);
            row.put("code", "UNIQUE_CODE");
            row.put("name", "Уникальный");
            row.put("color", "#3b82f6");
            row.put("sort_order", 1);
            row.put("is_final", false);
            row.put("is_system", false);

            when(jdbc.queryForList(contains("WHERE id ="), any()))
                .thenReturn(List.of(row));

            StatusDto.CreateRequest req = new StatusDto.CreateRequest();
            req.setCode("UNIQUE_CODE");
            req.setName("Уникальный");

            StatusDto.StatusResponse result = statusService.create("orders", req);

            assertThat(result.getCode()).isEqualTo("UNIQUE_CODE");
            assertThat(result.isSystem()).isFalse();
        }
    }

    // ── delete ────────────────────────────────────────────────────
    @Nested @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("системный статус — нельзя удалить 400")
        void systemStatus_throwsBadRequest() {
            UUID id = UUID.randomUUID();
            when(jdbc.queryForObject(
                contains("is_system"),
                eq(Boolean.class),
                eq(id))
            ).thenReturn(true);

            assertThatThrownBy(() -> statusService.delete("orders", id))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Системные");
        }

        @Test
        @DisplayName("статус используется в заказах — конфликт 409")
        void usedStatus_throwsConflict() {
            UUID id = UUID.randomUUID();
            when(jdbc.queryForObject(
                contains("is_system"),
                eq(Boolean.class),
                eq(id))
            ).thenReturn(false);

            when(jdbc.queryForObject(
                contains("COUNT(*)"),
                eq(Integer.class),
                eq(id))
            ).thenReturn(7); // 7 заказов используют этот статус

            assertThatThrownBy(() -> statusService.delete("orders", id))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("7");
        }

        @Test
        @DisplayName("пользовательский неиспользуемый статус — успешное удаление")
        void customUnused_success() {
            UUID id = UUID.randomUUID();
            when(jdbc.queryForObject(
                contains("is_system"),
                eq(Boolean.class),
                eq(id))
            ).thenReturn(false);

            when(jdbc.queryForObject(
                contains("COUNT(*)"),
                eq(Integer.class),
                eq(id))
            ).thenReturn(0);

            when(jdbc.update(contains("DELETE"), eq(id))).thenReturn(1);

            assertThatCode(() -> statusService.delete("orders", id))
                .doesNotThrowAnyException();

            verify(jdbc).update(contains("DELETE"), eq(id));
        }
    }

    // ── update ────────────────────────────────────────────────────
    @Nested @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("системный статус — нельзя полностью обновить 400")
        void systemStatus_throwsBadRequest() {
            UUID id = UUID.randomUUID();
            when(jdbc.queryForObject(
                contains("is_system"),
                eq(Boolean.class),
                eq(id))
            ).thenReturn(true);

            StatusDto.UpdateRequest req = new StatusDto.UpdateRequest();
            req.setName("Новое имя");
            req.setColor("#ff0000");

            assertThatThrownBy(() -> statusService.update("tasks", id, req))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Системные");
        }
    }
}
