package com.crm.rbac.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Атомарное право доступа.
 * Код пермиссии: MODULE_ACTION, например CUSTOMER_VIEW, TASK_CREATE, USER_MANAGE.
 */
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("permissions")
public class Permission {

    @Id
    private UUID id;

    private String code;
    private String name;
    private String description;
    private String module;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Permission p)) return false;
        return Objects.equals(id, p.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
