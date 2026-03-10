package com.crm.tenant.dto;

import lombok.Setter;
import lombok.Getter;
import lombok.Builder;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
public class TenantResponse {
    private UUID    id;
    private String  schemaName;
    private String  plan;
    private String  status;
    private Instant createdAt;
    // Из tenant_settings
    private String  companyName;
    private String  contactEmail;
    private String  contactPhone;
    private String  website;
    private String  logoUrl;
    private String  timezone;
    private String  currency;
    // Лимиты плана
    private int     maxUsers;
    private int     currentUsers;
}
