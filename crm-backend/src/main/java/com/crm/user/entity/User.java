package com.crm.user.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Сущность пользователя системы.
 * Маппинг на таблицу public.users (глобальная схема).
 *
 * Spring Data JDBC — явный, без магии:
 *  - нет lazy loading
 *  - нет каскадов по умолчанию
 *  - SQL-запросы очевидны
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "public", value = "users")
public class User {

    @Id
    private UUID id;

    private UUID tenantId;

    private String email;

    private String passwordHash;

    private String firstName;

    private String lastName;

    private String middleName;

    private String phone;

    private String avatarUrl;

    private String ssoProvider;

    private String ssoId;

    @Column("user_type")
    private UserType userType;

    @Column("status")
    private UserStatus status;

    private boolean emailVerified;

    private Instant createdAt;

    private Instant updatedAt;

    // ---- Вспомогательные методы ----

    public String getFullName() {
        StringBuilder sb = new StringBuilder();
        sb.append(lastName).append(" ").append(firstName);
        if (middleName != null && !middleName.isBlank()) {
            sb.append(" ").append(middleName);
        }
        return sb.toString();
    }

    public boolean isAdmin() {
        return UserType.ADMIN == userType;
    }

    public boolean isActive() {
        return UserStatus.ACTIVE == status;
    }
}
