package com.crm.tenant;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("public.tenants")
public class Tenant {

    @Id
    private UUID id;

    private String schemaName;
    private TenantPlan plan;
    private TenantStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tenant t)) return false;
        return Objects.equals(id, t.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
