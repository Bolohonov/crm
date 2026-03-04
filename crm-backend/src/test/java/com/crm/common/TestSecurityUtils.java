package com.crm.common;

import com.crm.auth.service.JwtService;
import com.crm.tenant.Tenant;
import com.crm.tenant.TenantPlan;
import com.crm.tenant.TenantStatus;
import com.crm.user.entity.User;
import com.crm.user.entity.UserStatus;
import com.crm.user.entity.UserType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

/**
 * Утилита для создания тестовых пользователей, тенантов и JWT-токенов.
 *
 * Использование:
 * <pre>
 *   User admin = TestSecurityUtils.adminUser();
 *   String token = jwtService.generateAccessToken(admin);
 *
 *   mockMvc.perform(get("/api/v1/users")
 *       .with(bearer(token)))
 *       .andExpect(status().isOk());
 * </pre>
 */
public final class TestSecurityUtils {

    private TestSecurityUtils() {}

    // ── Тестовые константы ────────────────────────────────────────
    public static final UUID TENANT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID ADMIN_ID   = UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final UUID MANAGER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    public static final String TEST_SCHEMA = "tenant_test_001";

    // ── Тестовые сущности ─────────────────────────────────────────
    public static Tenant testTenant() {
        return Tenant.builder()
            .id(TENANT_ID)
            .schemaName(TEST_SCHEMA)
            .plan(TenantPlan.STANDARD)
            .status(TenantStatus.ACTIVE)
            .createdAt(Instant.now())
            .build();
    }

    public static User adminUser() {
        return User.builder()
            .id(ADMIN_ID)
            .tenantId(TENANT_ID)
            .email("admin@test.local")
            .firstName("Иван")
            .lastName("Петров")
            .userType(UserType.ADMIN)
            .status(UserStatus.ACTIVE)
            .emailVerified(true)
            .createdAt(Instant.now())
            .build();
    }

    public static User managerUser() {
        return User.builder()
            .id(MANAGER_ID)
            .tenantId(TENANT_ID)
            .email("manager@test.local")
            .firstName("Анна")
            .lastName("Козлова")
            .userType(UserType.REGULAR)
            .status(UserStatus.ACTIVE)
            .emailVerified(true)
            .createdAt(Instant.now())
            .build();
    }

    public static User userWithId(UUID id, UserType type) {
        return User.builder()
            .id(id)
            .tenantId(TENANT_ID)
            .email(id + "@test.local")
            .firstName("Тест")
            .lastName("Пользователь")
            .userType(type)
            .status(UserStatus.ACTIVE)
            .emailVerified(true)
            .createdAt(Instant.now())
            .build();
    }

    // ── MockMvc helper ────────────────────────────────────────────
    public static MockHttpServletRequestBuilder withBearer(
            MockHttpServletRequestBuilder builder,
            String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
