package com.crm.auth.service;

import com.crm.auth.dto.AuthResponse;
import com.crm.common.config.AppProperties;
import com.crm.common.exception.AppException;
import com.crm.tenant.Tenant;
import com.crm.tenant.TenantRepository;
import com.crm.user.entity.User;
import com.crm.user.entity.UserStatus;
import com.crm.user.entity.UserType;
import com.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Сервис демо-авторизации.
 *
 * Стратегия:
 *  - В БД хранится фиксированный demo-пользователь (email = demo@crm.local)
 *    и demo-тенант (schema = tenant_demo), оба создаются миграцией V200.
 *  - При вызове loginAsDemo() находим этого пользователя и выпускаем обычную
 *    пару JWT — никаких спецхуков в JwtAuthFilter не нужно.
 *  - Одновременный вход нескольких людей — не проблема: JWT stateless,
 *    все работают в одной схеме tenant_demo и видят одни данные.
 *    Это — фича демо, а не баг.
 *  - CronJob раз в час дропает и пересоздаёт схему tenant_demo через
 *    TenantSchemaService — данные сбрасываются, токены остаются валидными
 *    (пользователь в public.users_global не меняется).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DemoAuthService {

    /** Фиксированный UUID demo-пользователя — задаётся миграцией V200. */
    public static final UUID DEMO_USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static final String DEMO_TENANT_SCHEMA = "tenant_demo";
    public static final String DEMO_USER_EMAIL    = "demo@crm.local";

    private final UserRepository    userRepository;
    private final TenantRepository  tenantRepository;
    private final JwtService        jwtService;
    private final TokenService      tokenService;
    private final AppProperties     appProperties;

    /**
     * Выпускает токены для demo-пользователя.
     * Не требует пароля — endpoint публичный.
     */
    public AuthResponse loginAsDemo() {
        User demoUser = userRepository.findById(DEMO_USER_ID)
                .orElseThrow(() -> AppException.notFound(
                        "Demo user not found. Run V200 migration first."));

        if (!demoUser.isActive()) {
            throw AppException.badRequest("DEMO_UNAVAILABLE",
                    "Демо временно недоступно. Попробуйте позже.");
        }

        // Проверяем что tenant_demo существует
        tenantRepository.findBySchemaName(DEMO_TENANT_SCHEMA)
                .orElseThrow(() -> AppException.notFound(
                        "Demo tenant schema not found."));

        String accessToken  = jwtService.generateAccessToken(demoUser, DEMO_TENANT_SCHEMA);
        String refreshToken = jwtService.generateRefreshToken();

        // Сохраняем refresh — при сбросе схемы CronJob не затрагивает public,
        // поэтому токен будет работать после ресета демо-данных
        tokenService.saveRefreshToken(demoUser.getId(), refreshToken);

        log.info("Demo login issued for session");

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(appProperties.getJwt().getAccessTokenExpiration())
                .userId(demoUser.getId())
                .email(DEMO_USER_EMAIL)
                .fullName("Демо Пользователь")
                .userType(UserType.ADMIN.name())
                .tenantSchema(DEMO_TENANT_SCHEMA)
                .build();
    }
}
