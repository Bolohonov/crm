package com.crm.user.dto;

import com.crm.user.entity.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class UserDto {

    // ── Ответы ────────────────────────────────────────────────────

    @Data @Builder
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

    @Data @Builder
    public static class RoleRef {
        private UUID   id;
        private String code;
        private String name;
        private String color;
    }

    @Data @Builder
    public static class PageResponse {
        private List<UserResponse> content;
        private int  page;
        private int  size;
        private long totalElements;
        private int  totalPages;
    }

    // ── Запросы ───────────────────────────────────────────────────

    /** Приглашение нового пользователя в тенант */
    @Data
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
    @Data
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
    @Data
    public static class ChangePasswordRequest {
        @NotBlank @Size(min = 8, max = 100)
        private String newPassword;
    }

    /** Собственная смена пароля (требует старый пароль) */
    @Data
    public static class SelfChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank @Size(min = 8, max = 100)
        private String newPassword;
    }

    /** Блокировка / разблокировка */
    @Data
    public static class SetStatusRequest {
        private UserStatus status;
        private String reason;
    }

    /** Принятие приглашения — установка пароля */
    @Data
    public static class AcceptInviteRequest {
        @NotBlank
        private String token;
        @NotBlank @Size(min = 8, max = 100)
        private String password;
    }

    /** Ответ на повторную отправку инвайта */
    @Data @Builder
    public static class ResendInviteResponse {
        private UUID   userId;
        private String email;
        private String message;
    }
}
