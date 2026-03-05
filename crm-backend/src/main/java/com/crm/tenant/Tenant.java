package com.crm.tenant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "public", value = "tenants")
public class Tenant {

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
