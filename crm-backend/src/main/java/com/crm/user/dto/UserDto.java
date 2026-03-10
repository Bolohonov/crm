package com.crm.user.dto;

import com.crm.user.entity.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class UserDto {

    // ── Ответы ────────────────────────────────────────────────────

    @Getter
    @Setter
    @EqualsAndHashCode @Builder
    public static class UserResponse {
        private UUID   id;
        private String email;
        private String firstName;
        private String lastName;
        private String middleName;
        private String phone;
        private String avatarUrl;
        private String userType;
        private String status;
        private boolean emailVerified;
        private Instant createdAt;
        private Instant lastLoginAt;
        /** Назначенные роли (только code + name + color) */
        private List<RoleRef> roles;
    }

    @Getter
    @Setter
    @EqualsAndHashCode @Builder
    public static class RoleRef {
        private UUID   id;
        private String code;
        private String name;
        private String color;
    }

    @Getter
    @Setter
    @EqualsAndHashCode @Builder
    public static class PageResponse {
        private List<UserResponse> content;
        private int  page;
        private int  size;
        private long totalElements;
        private int  totalPages;
    }

    // ── Запросы ───────────────────────────────────────────────────

    /** Приглашение нового пользователя в тенант */
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class InviteRequest {
        @NotBlank @Email
        private String email;
        @NotBlank @Size(max = 100)
        private String firstName;
        @NotBlank @Size(max = 100)
        private String lastName;
        @Size(max = 100)
        private String middleName;
        @Size(max = 20)
        private String phone;
        /** Список roleId для назначения (опционально) */
        private List<UUID> roleIds;
    }

    /** Обновление профиля пользователя (не email, не пароль) */
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class UpdateProfileRequest {
        @NotBlank @Size(max = 100)
        private String firstName;
        @NotBlank @Size(max = 100)
        private String lastName;
        @Size(max = 100)
        private String middleName;
        @Size(max = 20)
        private String phone;
    }

    /** Административная смена пароля */
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class ChangePasswordRequest {
        @NotBlank @Size(min = 8, max = 100)
        private String newPassword;
    }

    /** Собственная смена пароля (требует старый пароль) */
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class SelfChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank @Size(min = 8, max = 100)
        private String newPassword;
    }

    /** Блокировка / разблокировка */
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class SetStatusRequest {
        private UserStatus status;
        private String reason;
    }

    /** Принятие приглашения — установка пароля */
    @Getter
    @Setter
    @EqualsAndHashCode
    public static class AcceptInviteRequest {
        @NotBlank
        private String token;
        @NotBlank @Size(min = 8, max = 100)
        private String password;
    }

    /** Ответ на повторную отправку инвайта */
    @Getter
    @Setter
    @EqualsAndHashCode @Builder
    public static class ResendInviteResponse {
        private UUID   userId;
        private String email;
        private String message;
    }
}
