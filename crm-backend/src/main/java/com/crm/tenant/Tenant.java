package com.crm.tenant;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(schema = "public", value = "tenants")
public class Tenant {
    @EqualsAndHashCode.Include
    @Id
    private UUID id;

    private String name;        // NOT NULL в БД
    private String slug;        // NOT NULL в БД
    private String schemaName;
    private TenantPlan plan;
    private TenantStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
