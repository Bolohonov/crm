package com.crm.rbac.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Роль пользователя внутри тенанта.
 * Таблица в схеме тенанта — search_path уже установлен JwtAuthenticationFilter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("roles")
public class Role {

    @Id
    private UUID id;

    private String code;

    private String name;

    private String description;

    private boolean isSystem;

    private Instant createdAt;
}
