package com.crm.user.service;

import com.crm.auth.entity.EmailVerification;
import com.crm.auth.entity.EmailVerificationType;
import com.crm.auth.repository.EmailVerificationRepository;
import com.crm.auth.service.EmailService;
import com.crm.common.exception.AppException;
import com.crm.rbac.entity.Role;
import com.crm.rbac.entity.UserRole;
import com.crm.rbac.repository.RoleRepository;
import com.crm.rbac.repository.UserRoleRepository;
import com.crm.tenant.Tenant;
import com.crm.tenant.TenantContext;
import com.crm.tenant.TenantPlan;
import com.crm.tenant.TenantRepository;
import com.crm.user.dto.UserDto;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Сервис приглашения новых пользователей в тенант.
 *
 * Поток:
 *  1. Admin вызывает invite() → создаётся User(PENDING) + EmailVerification(INVITE)
 *  2. Письмо уходит на email приглашаемого
 *  3. Пользователь переходит по ссылке → acceptInvite() → User(ACTIVE), устанавливает пароль
 *
 * Проверки:
 *  - email не занят в этом тенанте
 *  - не превышен лимит пользователей по плану тенанта
 *  - роли существуют в этом тенанте
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {

    private final UserRepository               userRepository;
    private final RoleRepository               roleRepository;
    private final UserRoleRepository           userRoleRepository;
    private final EmailVerificationRepository  verificationRepository;
    private final EmailService                 emailService;
    private final TenantRepository             tenantRepository;
    private final PasswordEncoder              passwordEncoder;

    private static final int INVITE_EXPIRE_DAYS = 7;

    // ── Лимиты по плану ───────────────────────────────────────────
    private static final java.util.Map<TenantPlan, Integer> PLAN_LIMITS = java.util.Map.of(
        TenantPlan.FREE,       5,
        TenantPlan.STANDARD,   25,
        TenantPlan.ENTERPRISE, Integer.MAX_VALUE
    );

    // ── Пригласить пользователя ───────────────────────────────────
    @Transactional
    public UserDto.UserResponse invite(UserDto.InviteRequest req) {
        Tenant tenant    = TenantContext.get();
        UUID   tenantId  = tenant.getId();

        // 1. Лимит пользователей
        int maxUsers = PLAN_LIMITS.getOrDefault(tenant.getPlan(), 5);
        int current  = userRepository.countActiveByTenantId(tenantId);
        if (current >= maxUsers) {
            throw AppException.badRequest("USER_LIMIT_REACHED",
                "Достигнут лимит пользователей (%d) для плана %s".formatted(maxUsers, tenant.getPlan()));
        }

        // 2. Email не занят в тенанте
        if (userRepository.existsByEmailAndTenantId(req.getEmail(), tenantId)) {
            throw AppException.conflict("EMAIL_IN_TENANT",
                "Пользователь с email " + req.getEmail() + " уже существует в этом тенанте");
        }

        // 3. Роли существуют
        List<Role> roles = req.getRoleIds() != null
            ? req.getRoleIds().stream()
                .map(id -> roleRepository.findById(id)
                    .orElseThrow(() -> AppException.notFound("Role " + id)))
                .toList()
            : List.of();

        // 4. Создаём пользователя со статусом PENDING
        User user = User.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .email(req.getEmail().toLowerCase().strip())
            .firstName(req.getFirstName())
            .lastName(req.getLastName())
            .middleName(req.getMiddleName())
            .phone(req.getPhone())
            .passwordHash("")                          // пустой — установит при принятии инвайта
            .userType(UserType.EMPLOYEE)
            .status(UserStatus.PENDING)
            .emailVerified(false)
            .createdAt(Instant.now())
            .build();
        userRepository.save(user);

        // 5. Назначаем роли
        for (Role role : roles) {
            userRoleRepository.save(UserRole.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .roleId(role.getId())
                .build());
        }

        // 6. Создаём токен приглашения
        String token = UUID.randomUUID().toString().replace("-", "");
        verificationRepository.save(EmailVerification.builder()
            .id(UUID.randomUUID())
            .userId(user.getId())
            .token(token)
            .type(EmailVerificationType.INVITE)
            .expiresAt(Instant.now().plus(INVITE_EXPIRE_DAYS, ChronoUnit.DAYS))
            .build());

        // 7. Отправляем письмо
        try {
            emailService.sendInvite(
                user.getEmail(),
                user.getFirstName(),
                tenant.getSchemaName(),  // имя компании как fallback
                token
            );
        } catch (Exception e) {
            log.error("Failed to send invite email to {}: {}", user.getEmail(), e.getMessage());
            // Не кидаем — пользователь создан, токен есть, письмо можно переслать
        }

        log.info("Invited user {} to tenant {}", user.getEmail(), tenantId);
        return toResponse(user, roles);
    }

    // ── Принять приглашение (установить пароль) ───────────────────
    @Transactional
    public void acceptInvite(String token, String newPassword) {
        EmailVerification v = verificationRepository.findByTokenAndType(token, EmailVerificationType.INVITE)
            .orElseThrow(() -> AppException.badRequest("INVALID_TOKEN", "Токен приглашения недействителен"));

        if (v.getExpiresAt().isBefore(Instant.now())) {
            throw AppException.badRequest("TOKEN_EXPIRED",
                "Приглашение истекло. Попросите администратора выслать новое.");
        }
        if (v.isUsed()) {
            throw AppException.badRequest("TOKEN_USED", "Приглашение уже было использовано");
        }

        // Устанавливаем пароль и активируем
        userRepository.updatePassword(v.getUserId(), passwordEncoder.encode(newPassword));
        userRepository.updateStatus(v.getUserId(), UserStatus.ACTIVE.name(), Instant.now());
        userRepository.setEmailVerified(v.getUserId());

        // Помечаем токен использованным
        v.setUsed(true);
        v.setUsedAt(Instant.now());
        verificationRepository.save(v);

        log.info("Invite accepted for userId={}", v.getUserId());
    }

    // ── Повторная отправка приглашения ────────────────────────────
    @Transactional
    public void resendInvite(UUID userId) {
        Tenant tenant = TenantContext.get();
        User user = userRepository.findById(userId)
            .filter(u -> tenant.getId().equals(u.getTenantId()))
            .orElseThrow(() -> AppException.notFound("User"));

        if (user.getStatus() != UserStatus.PENDING) {
            throw AppException.badRequest("USER_NOT_PENDING",
                "Пользователь уже принял приглашение или заблокирован");
        }

        // Инвалидируем старые токены
        verificationRepository.invalidateOldTokens(userId, EmailVerificationType.INVITE);

        // Новый токен
        String token = UUID.randomUUID().toString().replace("-", "");
        verificationRepository.save(EmailVerification.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .token(token)
            .type(EmailVerificationType.INVITE)
            .expiresAt(Instant.now().plus(INVITE_EXPIRE_DAYS, ChronoUnit.DAYS))
            .build());

        emailService.sendInvite(user.getEmail(), user.getFirstName(), tenant.getSchemaName(), token);
        log.info("Resent invite to userId={}", userId);
    }

    // ── Маппинг ───────────────────────────────────────────────────
    private UserDto.UserResponse toResponse(User u, List<Role> roles) {
        return UserDto.UserResponse.builder()
            .id(u.getId())
            .email(u.getEmail())
            .firstName(u.getFirstName())
            .lastName(u.getLastName())
            .middleName(u.getMiddleName())
            .phone(u.getPhone())
            .userType(u.getUserType().name())
            .status(u.getStatus().name())
            .emailVerified(u.isEmailVerified())
            .createdAt(u.getCreatedAt())
            .roles(roles.stream()
                .map(r -> UserDto.RoleRef.builder()
                    .id(r.getId()).code(r.getCode()).name(r.getName()).color(r.getColor())
                    .build())
                .toList())
            .build();
    }
}
