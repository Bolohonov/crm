package com.crm.rbac.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Связь пользователя с ролью в тенанте.
 * Составной PK: (user_id, role_id) — без суррогатного id.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_roles")
public class UserRole {

    private UUID userId;       // ссылка на public.users.id

    private UUID roleId;       // ссылка на tenant_xxx.roles.id

    private Instant assignedAt;

    private UUID assignedBy;   // кто назначил
}
