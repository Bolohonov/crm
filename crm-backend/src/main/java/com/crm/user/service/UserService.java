package com.crm.user.service;

import com.crm.common.exception.AppException;
import com.crm.rbac.entity.Role;
import com.crm.rbac.entity.UserRole;
import com.crm.rbac.repository.RoleRepository;
import com.crm.rbac.repository.UserRoleRepository;
import com.crm.tenant.TenantContext;
import com.crm.user.dto.UserDto;
import com.crm.user.entity.User;
import com.crm.user.entity.UserStatus;
import com.crm.user.entity.UserType;
import com.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.StreamSupport;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Список пользователей тенанта ──────────────────────────────
    public UserDto.PageResponse list(int page, int size, String q) {
        UUID tenantId = TenantContext.getTenant().getId();

        List<User> all = StreamSupport
            .stream(userRepository.findAllByTenantId(tenantId).spliterator(), false)
            .filter(u -> q == null || q.isBlank() || matchesQuery(u, q))
            .toList();

        int total = all.size();
        int from  = Math.min(page * size, total);
        int to    = Math.min(from + size, total);
        List<User> slice = all.subList(from, to);

        return UserDto.PageResponse.builder()
            .content(slice.stream().map(this::toResponse).toList())
            .page(page).size(size)
            .totalElements(total)
            .totalPages((int) Math.ceil((double) total / size))
            .build();
    }

    // ── Получить пользователя ─────────────────────────────────────
    public UserDto.UserResponse getById(UUID id) {
        User user = findInTenant(id);
        return toResponse(user);
    }

    // ── Обновить профиль ──────────────────────────────────────────
    @Transactional
    public UserDto.UserResponse updateProfile(UUID id, UserDto.UpdateProfileRequest req) {
        User user = findInTenant(id);
        user.setFirstName(req.getFirstName());
        user.setLastName(req.getLastName());
        user.setMiddleName(req.getMiddleName());
        user.setPhone(req.getPhone());
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        return toResponse(user);
    }

    // ── Изменить статус (блокировка/разблокировка) ────────────────
    @Transactional
    public void setStatus(UUID id, UserStatus newStatus, UUID actorId) {
        User user = findInTenant(id);
        if (user.getId().equals(actorId)) {
            throw AppException.badRequest("SELF_STATUS", "Нельзя изменить собственный статус");
        }
        if (user.getUserType() == UserType.ADMIN) {
            throw AppException.forbidden("Нельзя блокировать администратора");
        }
        userRepository.updateStatus(id, newStatus.name(), Instant.now());
    }

    // ── Сменить пароль (администратором) ──────────────────────────
    @Transactional
    public void changePassword(UUID id, String newPassword) {
        User user = findInTenant(id);
        userRepository.updatePassword(id, passwordEncoder.encode(newPassword));
    }

    // ── Собственная смена пароля ──────────────────────────────────
    @Transactional
    public void selfChangePassword(UUID id, String currentPassword, String newPassword) {
        User user = findInTenant(id);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw AppException.badRequest("WRONG_PASSWORD", "Текущий пароль неверен");
        }
        userRepository.updatePassword(id, passwordEncoder.encode(newPassword));
    }

    // ── Удалить пользователя из тенанта (деактивировать) ─────────
    @Transactional
    public void deactivate(UUID id, UUID actorId) {
        User user = findInTenant(id);
        if (user.getId().equals(actorId)) {
            throw AppException.badRequest("SELF_DEACTIVATE", "Нельзя деактивировать себя");
        }
        userRepository.updateStatus(id, UserStatus.BLOCKED.name(), Instant.now());
        // Отзываем все роли
        userRoleRepository.deleteAllByUserId(id);
    }

    // ── Приватные утилиты ─────────────────────────────────────────
    private User findInTenant(UUID id) {
        UUID tenantId = TenantContext.getTenant().getId();
        return userRepository.findById(id)
            .filter(u -> tenantId.equals(u.getTenantId()))
            .orElseThrow(() -> AppException.notFound("User"));
    }

    private boolean matchesQuery(User u, String q) {
        String lq = q.toLowerCase();
        return (u.getFirstName() != null && u.getFirstName().toLowerCase().contains(lq))
            || (u.getLastName()  != null && u.getLastName().toLowerCase().contains(lq))
            || (u.getEmail()     != null && u.getEmail().toLowerCase().contains(lq));
    }

    private UserDto.UserResponse toResponse(User u) {
        List<UserDto.RoleRef> roles = loadRoles(u.getId());
        return UserDto.UserResponse.builder()
            .id(u.getId())
            .email(u.getEmail())
            .firstName(u.getFirstName())
            .lastName(u.getLastName())
            .middleName(u.getMiddleName())
            .phone(u.getPhone())
            .avatarUrl(u.getAvatarUrl())
            .userType(u.getUserType() != null ? u.getUserType().name() : null)
            .status(u.getStatus() != null ? u.getStatus().name() : null)
            .emailVerified(u.isEmailVerified())
            .createdAt(u.getCreatedAt())
            .roles(roles)
            .build();
    }

    private List<UserDto.RoleRef> loadRoles(UUID userId) {
        return StreamSupport
            .stream(userRoleRepository.findByUserId(userId).spliterator(), false)
            .map(ur -> roleRepository.findById(ur.getRoleId()).orElse(null))
            .filter(Objects::nonNull)
            .map(r -> (UserDto.RoleRef) UserDto.RoleRef.builder()
                .id(r.getId())
                .code(r.getCode())
                .name(r.getName())
                .color(r.getColor())
                .build())
            .toList();
    }
}
