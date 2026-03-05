package com.crm.auth.service;

import com.crm.auth.dto.*;
import com.crm.common.config.AppProperties;
import com.crm.common.exception.AppException;
import com.crm.tenant.*;
import com.crm.user.entity.User;
import com.crm.user.entity.UserStatus;
import com.crm.user.entity.UserType;
import com.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Основная бизнес-логика аутентификации.
 *
 * Сценарий регистрации ADMIN:
 *  1. Проверяем уникальность email
 *  2. Создаём User (status=PENDING)
 *  3. Создаём Tenant (status=PENDING)
 *  4. Провизионируем PostgreSQL-схему тенанта (Liquibase)
 *  5. Привязываем User к Tenant
 *  6. Отправляем email для верификации
 *
 * Сценарий регистрации REGULAR:
 *  1. Проверяем уникальность email
 *  2. Находим администратора по adminEmail
 *  3. Создаём User (status=PENDING, tenant_id = тенант администратора)
 *  4. Отправляем инвайт администратору для подтверждения
 *
 * После верификации email:
 *  - ADMIN: Tenant.status → ACTIVE, User.status → ACTIVE
 *  - REGULAR: User.status → PENDING (ждёт подтверждения от администратора)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantSchemaService tenantSchemaService;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;

    // ----------------------------------------------------------------
    //  Регистрация
    // ----------------------------------------------------------------

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Проверяем уникальность email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw AppException.conflict("EMAIL_ALREADY_EXISTS",
                "Пользователь с таким email уже зарегистрирован");
        }

        return switch (request.getUserType()) {
            case ADMIN   -> registerAdmin(request);
            case REGULAR, EMPLOYEE -> registerRegular(request);
        };
    }

    private RegisterResponse registerAdmin(RegisterRequest request) {
        // 1. Создаём пользователя
        User user = createUser(request, UserType.ADMIN, null);

        // 2. Создаём запись тенанта (схема ещё не создана)
        Tenant tenant = Tenant.builder()
                .name(request.getFirstName() + " " + request.getLastName())
                .slug(UUID.randomUUID().toString().substring(0, 8))
                .plan(request.getPlan() != null ? request.getPlan() : TenantPlan.FREE)
                .status(TenantStatus.PENDING)
                .schemaName("")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        tenant = tenantRepository.save(tenant);

        // 3. Провизионируем PostgreSQL схему и применяем Liquibase миграции
        String schemaName = tenantSchemaService.provisionTenantSchema(tenant.getId());

        // 4. Обновляем схему в тенанте и привязываем пользователя
        tenant.setSchemaName(schemaName);
        tenant.setUpdatedAt(Instant.now());
        tenantRepository.save(tenant);

        userRepository.assignTenant(user.getId(), tenant.getId());

        // 5. Отправляем письмо верификации (асинхронно)
        emailService.sendRegistrationConfirmation(user);

        log.info("Admin registered: {}, tenant: {}", user.getEmail(), schemaName);
        return RegisterResponse.builder()
            .message("Регистрация успешна. Проверьте email для подтверждения аккаунта.")
            .email(user.getEmail())
            .build();
    }

    private RegisterResponse registerRegular(RegisterRequest request) {
        if (request.getAdminEmail() == null || request.getAdminEmail().isBlank()) {
            throw AppException.badRequest("ADMIN_EMAIL_REQUIRED",
                "Для обычного пользователя необходимо указать email администратора");
        }

        // Находим администратора
        User admin = userRepository.findByEmail(request.getAdminEmail())
            .filter(u -> u.getUserType() == UserType.ADMIN)
            .filter(User::isActive)
            .orElseThrow(() -> AppException.badRequest("ADMIN_NOT_FOUND",
                "Администратор с таким email не найден или не активен"));

        // Проверяем лимит пользователей для FREE плана
        Tenant adminTenant = tenantRepository.findById(admin.getTenantId())
            .orElseThrow(() -> AppException.notFound("Тенант"));

        if (adminTenant.getPlan() == TenantPlan.FREE) {
            int activeUsers = userRepository.countActiveByTenantId(admin.getTenantId());
            if (activeUsers >= appProperties.getTenant().getFreePlanUserLimit()) {
                throw AppException.conflict("USER_LIMIT_REACHED",
                    "Достигнут лимит пользователей для бесплатного тарифа");
            }
        }

        // Создаём пользователя с тенантом администратора
        User user = createUser(request, UserType.REGULAR, admin.getTenantId());

        // Отправляем инвайт администратору для подтверждения
        emailService.sendInviteToAdmin(user, admin);

        log.info("Regular user registered: {}, admin: {}", user.getEmail(), admin.getEmail());
        return RegisterResponse.builder()
            .message("Запрос на доступ отправлен. Администратор получит уведомление для подтверждения.")
            .email(user.getEmail())
            .build();
    }

    // ----------------------------------------------------------------
    //  Верификация email
    // ----------------------------------------------------------------

    @Transactional
    public void verifyEmail(String token) {
        UUID userId = emailService.validateVerificationToken(token);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> AppException.notFound("Пользователь"));

        // Активируем email
        userRepository.verifyEmail(userId);

        // Для ADMIN — активируем тенант
        if (user.getUserType() == UserType.ADMIN && user.getTenantId() != null) {
            tenantRepository.updateStatus(user.getTenantId(), TenantStatus.ACTIVE.name());
        }

        log.info("Email verified for user: {}", user.getEmail());
    }

    // ----------------------------------------------------------------
    //  Логин
    // ----------------------------------------------------------------

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new org.springframework.security.authentication
                .BadCredentialsException("Invalid credentials"));

        if (user.getPasswordHash() == null ||
            !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new org.springframework.security.authentication
                .BadCredentialsException("Invalid credentials");
        }

        if (!user.isEmailVerified()) {
            throw AppException.badRequest("EMAIL_NOT_VERIFIED",
                "Email не подтверждён. Проверьте почту.");
        }

        if (!user.isActive()) {
            throw AppException.badRequest("USER_BLOCKED",
                "Аккаунт заблокирован. Обратитесь к администратору.");
        }

        return issueTokens(user);
    }

    // ----------------------------------------------------------------
    //  Обновление токенов
    // ----------------------------------------------------------------

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        UUID userId = tokenService.validateAndRotate(rawRefreshToken);

        User user = userRepository.findById(userId)
            .filter(User::isActive)
            .orElseThrow(() -> AppException.unauthorized("Пользователь не найден или заблокирован"));

        return issueTokens(user);
    }

    // ----------------------------------------------------------------
    //  Выход из системы
    // ----------------------------------------------------------------

    @Transactional
    public void logout(UUID userId) {
        tokenService.revokeAllUserTokens(userId);
        log.info("User logged out: {}", userId);
    }

    // ----------------------------------------------------------------
    //  Вспомогательные методы
    // ----------------------------------------------------------------

    private AuthResponse issueTokens(User user) {
        // Получаем схему тенанта для JWT payload
        String tenantSchema = null;
        if (user.getTenantId() != null) {
            tenantSchema = tenantRepository.findById(user.getTenantId())
                .map(Tenant::getSchemaName)
                .orElse(null);
        }

        String accessToken  = jwtService.generateAccessToken(user, tenantSchema);
        String refreshToken = jwtService.generateRefreshToken();

        tokenService.saveRefreshToken(user.getId(), refreshToken);

        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiresIn(appProperties.getJwt().getAccessTokenExpiration())
            .userId(user.getId())
            .email(user.getEmail())
            .fullName(user.getFullName())
            .userType(user.getUserType().name())
            .tenantSchema(tenantSchema)
            .build();
    }

    private User createUser(RegisterRequest request, UserType userType, UUID tenantId) {
        User user = User.builder()
            .email(request.getEmail().toLowerCase().trim())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .firstName(request.getFirstName().trim())
            .lastName(request.getLastName().trim())
            .middleName(request.getMiddleName() != null ? request.getMiddleName().trim() : null)
            .phone(request.getPhone())
            .userType(userType)
            .status(UserStatus.PENDING)
            .tenantId(tenantId)
            .emailVerified(false)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        return userRepository.save(user);
    }
}
