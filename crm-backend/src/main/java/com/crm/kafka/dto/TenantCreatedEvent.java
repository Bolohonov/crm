package com.crm.kafka.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Событие активации нового тенанта в CRM.
 *
 * Топик:    crm.tenant.created
 * Источник: CRM → Shop
 *
 * Магазин получает это событие и сохраняет crmTenantSchema
 * в профиле пользователя по adminEmail.
 */
@Data
@Builder
public class TenantCreatedEvent {
    private UUID tenantId;
    private String tenantSchema;
    private String adminEmail;
    private Instant activatedAt;
}
