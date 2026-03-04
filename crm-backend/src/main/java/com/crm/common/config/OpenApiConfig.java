package com.crm.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Конфигурация OpenAPI (Swagger UI).
 *
 * Доступно по адресу:
 *   http://localhost:8080/swagger-ui.html
 *   http://localhost:8080/v3/api-docs
 *
 * В production Swagger UI отключается через:
 *   springdoc.swagger-ui.enabled=false
 */
@Configuration
public class OpenApiConfig {

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Bean
    public OpenAPI openAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
            .info(new Info()
                .title("CRM Cloud API")
                .version("0.1.0")
                .description("""
                    **Cloud CRM** — мультитенантная CRM-система.

                    ## Аутентификация
                    Все защищённые эндпоинты требуют JWT Bearer-токен в заголовке:
                    ```
                    Authorization: Bearer <access_token>
                    ```
                    Токен получается через `POST /api/v1/auth/login`.

                    ## Мультитенантность
                    Тенант определяется автоматически из JWT-токена (поле `tenantId`).
                    Данные изолированы по схемам PostgreSQL (`tenant_<uuid>`).

                    ## Коды ошибок
                    | HTTP | Код | Описание |
                    |------|-----|----------|
                    | 400 | `VALIDATION_ERROR` | Ошибка валидации входных данных |
                    | 401 | `UNAUTHORIZED` | Токен не передан или просрочен |
                    | 403 | `FORBIDDEN` | Недостаточно прав |
                    | 404 | `NOT_FOUND` | Ресурс не найден |
                    | 409 | `CONFLICT` | Конфликт (дублирующийся email и т.п.) |
                    """)
                .contact(new Contact()
                    .name("CRM Cloud Team")
                    .email("dev@crm.local"))
                .license(new License()
                    .name("Proprietary")
                    .url("#")))
            .servers(List.of(
                new Server().url(baseUrl + "/api/v1").description("Current server"),
                new Server().url("http://localhost:8080/api/v1").description("Local dev")
            ))
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                    .name(securitySchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("JWT access token. Получите через POST /auth/login")));
    }

    // ── Группы API для удобной навигации в Swagger UI ─────────────

    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
            .group("1-auth")
            .displayName("🔑 Аутентификация")
            .pathsToMatch("/auth/**")
            .build();
    }

    @Bean
    public GroupedOpenApi usersApi() {
        return GroupedOpenApi.builder()
            .group("2-users")
            .displayName("👤 Пользователи")
            .pathsToMatch("/users/**")
            .build();
    }

    @Bean
    public GroupedOpenApi customersApi() {
        return GroupedOpenApi.builder()
            .group("3-customers")
            .displayName("🏢 Клиенты")
            .pathsToMatch("/customers/**")
            .build();
    }

    @Bean
    public GroupedOpenApi ordersApi() {
        return GroupedOpenApi.builder()
            .group("4-orders")
            .displayName("📦 Заказы")
            .pathsToMatch("/orders/**")
            .build();
    }

    @Bean
    public GroupedOpenApi tasksApi() {
        return GroupedOpenApi.builder()
            .group("5-tasks")
            .displayName("✅ Задачи")
            .pathsToMatch("/tasks/**")
            .build();
    }

    @Bean
    public GroupedOpenApi dashboardApi() {
        return GroupedOpenApi.builder()
            .group("6-dashboard")
            .displayName("📊 Дашборд")
            .pathsToMatch("/dashboard/**")
            .build();
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
            .group("7-admin")
            .displayName("⚙️ Администрирование")
            .pathsToMatch("/tenant/**", "/users/**", "/rbac/**", "/roles/**",
                          "/permissions/**", "/statuses/**", "/audit/**")
            .build();
    }

    @Bean
    public GroupedOpenApi allApi() {
        return GroupedOpenApi.builder()
            .group("0-all")
            .displayName("📚 Все эндпоинты")
            .pathsToMatch("/**")
            .build();
    }
}
