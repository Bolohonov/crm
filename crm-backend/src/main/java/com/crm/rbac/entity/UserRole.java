package com.crm.rbac.entity;

import lombok.*;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Связь пользователя с ролью в тенанте.
 * Составной PK: (user_id, role_id) — без суррогатного id.
 */
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("user_roles")
public class UserRole {

    private UUID userId;
    private UUID roleId;
    private Instant assignedAt;
    private UUID assignedBy;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRole ur)) return false;
        return Objects.equals(userId, ur.userId)
            && Objects.equals(roleId, ur.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}
