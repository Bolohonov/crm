package com.crm.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

public class TenantDto {

    @Data @Builder
    public static class TenantResponse {
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

    @Data
    public static class UpdateSettingsRequest {
        @NotBlank @Size(max = 200)
        private String companyName;

        @Email @Size(max = 200)
        private String contactEmail;

        @Size(max = 20)
        private String contactPhone;

        @Size(max = 200)
        private String website;

        @Size(max = 500)
        private String logoUrl;

        @Size(max = 50)
        private String timezone;

        @Size(max = 10)
        private String currency;
    }

    @Data @Builder
    public static class ModuleResponse {
        private String  code;
        private String  name;
        private String  description;
        private boolean enabled;
        private boolean required;
    }

    @Data
    public static class SetModuleRequest {
        private boolean enabled;
    }
}
