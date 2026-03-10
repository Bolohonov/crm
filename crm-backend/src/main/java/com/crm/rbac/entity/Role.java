package com.crm.rbac.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
@Table("roles")
public class Role {

    @Id
    private UUID id;

    private String code;
    private String name;
    private String description;
    private String color;       // цвет бейджа роли в UI, например "#6366f1"
    private boolean isSystem;
    private Instant createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Role r)) return false;
        return Objects.equals(id, r.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
