package com.crm.tenant.service;

import com.crm.tenant.dto.ModuleResponse;
import com.crm.tenant.dto.TenantResponse;
import com.crm.tenant.dto.UpdateSettingsRequest;

import com.crm.common.exception.AppException;
import com.crm.tenant.Tenant;
import com.crm.tenant.TenantContext;
import com.crm.tenant.TenantRepository;
import com.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository   userRepository;
    private final JdbcTemplate     jdbc;

    // Описания модулей (в реальном проекте — из БД или конфига)
    private static final List<Map<String, Object>> MODULE_META = List.of(
        Map.of("code", "CUSTOMERS", "name", "Клиенты",     "description", "Управление клиентской базой",          "required", true),
        Map.of("code", "TASKS",     "name", "Задачи",       "description", "Планирование задач и напоминания",     "required", false),
        Map.of("code", "ORDERS",    "name", "Заказы",       "description", "Воронка продаж и управление заказами", "required", false),
        Map.of("code", "PRODUCTS",  "name", "Товары",       "description", "Каталог товаров и услуг",             "required", false),
        Map.of("code", "REPORTS",   "name", "Отчёты",       "description", "Аналитика и выгрузки",                "required", false)
    );

    private static final Map<String, Integer> PLAN_USER_LIMITS = Map.of(
        "FREE",     5,
        "STANDARD", 25,
        "ENTERPRISE", Integer.MAX_VALUE
    );

    // ── Профиль тенанта ───────────────────────────────────────────
    public TenantResponse getProfile() {
        Tenant tenant = TenantContext.getTenant();
        String schema = tenant.getSchemaName();

        // Настройки из tenant_settings (если таблица есть)
        Map<String, Object> settings = loadSettings(schema);

        int maxUsers     = PLAN_USER_LIMITS.getOrDefault(tenant.getPlan().name(), 5);
        int currentUsers = userRepository.countActiveByTenantId(tenant.getId());

        return TenantResponse.builder()
            .id(tenant.getId())
            .schemaName(schema)
            .plan(tenant.getPlan().name())
            .status(tenant.getStatus().name())
            .createdAt(tenant.getCreatedAt())
            .companyName((String) settings.getOrDefault("company_name", ""))
            .contactEmail((String) settings.getOrDefault("contact_email", ""))
            .contactPhone((String) settings.getOrDefault("contact_phone", ""))
            .website((String) settings.getOrDefault("website", ""))
            .logoUrl((String) settings.getOrDefault("logo_url", ""))
            .timezone((String) settings.getOrDefault("timezone", "Europe/Moscow"))
            .currency((String) settings.getOrDefault("currency", "RUB"))
            .maxUsers(maxUsers)
            .currentUsers(currentUsers)
            .build();
    }

    // ── Обновить настройки ────────────────────────────────────────
    @Transactional
    public TenantResponse updateSettings(UpdateSettingsRequest req) {
        Tenant tenant = TenantContext.getTenant();
        String schema = tenant.getSchemaName();
        upsertSettings(schema, req);
        return getProfile();
    }

    // ── Модули ────────────────────────────────────────────────────
    public List<ModuleResponse> getModules() {
        Tenant tenant = TenantContext.getTenant();
        Set<String> enabled = loadEnabledModules(tenant.getId());

        return MODULE_META.stream().map(meta -> {
            String code = (String) meta.get("code");
            return ModuleResponse.builder()
                .code(code)
                .name((String) meta.get("name"))
                .description((String) meta.get("description"))
                .enabled(enabled.contains(code) || Boolean.TRUE.equals(meta.get("required")))
                .required(Boolean.TRUE.equals(meta.get("required")))
                .build();
        }).toList();
    }

    // ── Включить/выключить модуль ─────────────────────────────────
    @Transactional
    public void setModuleEnabled(String moduleCode, boolean enabled) {
        // Проверяем что такой модуль существует
        boolean known = MODULE_META.stream()
            .anyMatch(m -> moduleCode.equalsIgnoreCase((String) m.get("code")));
        if (!known) {
            throw AppException.badRequest("UNKNOWN_MODULE",
                "Неизвестный модуль: " + moduleCode);
        }

        // Нельзя отключить обязательный
        MODULE_META.stream()
            .filter(m -> moduleCode.equalsIgnoreCase((String) m.get("code")))
            .findFirst()
            .ifPresent(m -> {
                if (Boolean.TRUE.equals(m.get("required")) && !enabled) {
                    throw AppException.badRequest("MODULE_REQUIRED",
                        "Модуль '" + moduleCode + "' является обязательным и не может быть отключён");
                }
            });

        Tenant tenant = TenantContext.getTenant();
        jdbc.update(
            "INSERT INTO public.tenant_modules (tenant_id, module_code, is_enabled, updated_at) " +
            "VALUES (?, ?, ?, NOW()) " +
            "ON CONFLICT (tenant_id, module_code) DO UPDATE SET is_enabled = ?, updated_at = NOW()",
            tenant.getId(), moduleCode.toUpperCase(), enabled,
            enabled
        );
    }

    // ── Приватные утилиты ─────────────────────────────────────────
    private Map<String, Object> loadSettings(String schema) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT company_name, contact_email, contact_phone, " +
                "       website, logo_url, timezone, currency " +
                "FROM " + schema + ".tenant_settings LIMIT 1"
            );
            return rows.isEmpty() ? Map.of() : rows.get(0);
        } catch (Exception e) {
            // Таблица может не существовать в старых схемах
            return Map.of();
        }
    }

    private void upsertSettings(String schema, UpdateSettingsRequest req) {
        // Создаём таблицу если не существует
        jdbc.execute(
            "CREATE TABLE IF NOT EXISTS " + schema + ".tenant_settings (" +
            "  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(), " +
            "  company_name  TEXT, " +
            "  contact_email TEXT, " +
            "  contact_phone TEXT, " +
            "  website       TEXT, " +
            "  logo_url      TEXT, " +
            "  timezone      TEXT DEFAULT 'Europe/Moscow', " +
            "  currency      TEXT DEFAULT 'RUB', " +
            "  updated_at    TIMESTAMPTZ DEFAULT NOW()" +
            ")"
        );

        int count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM " + schema + ".tenant_settings", Integer.class
        );

        if (count == 0) {
            jdbc.update(
                "INSERT INTO " + schema + ".tenant_settings " +
                "(company_name, contact_email, contact_phone, website, logo_url, timezone, currency) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                req.getCompanyName(), req.getContactEmail(), req.getContactPhone(),
                req.getWebsite(), req.getLogoUrl(),
                req.getTimezone() != null ? req.getTimezone() : "Europe/Moscow",
                req.getCurrency() != null ? req.getCurrency() : "RUB"
            );
        } else {
            jdbc.update(
                "UPDATE " + schema + ".tenant_settings SET " +
                "company_name = ?, contact_email = ?, contact_phone = ?, " +
                "website = ?, logo_url = ?, timezone = ?, currency = ?, updated_at = NOW()",
                req.getCompanyName(), req.getContactEmail(), req.getContactPhone(),
                req.getWebsite(), req.getLogoUrl(),
                req.getTimezone() != null ? req.getTimezone() : "Europe/Moscow",
                req.getCurrency() != null ? req.getCurrency() : "RUB"
            );
        }
    }

    private Set<String> loadEnabledModules(UUID tenantId) {
        List<String> codes = jdbc.queryForList(
            "SELECT module_code FROM public.tenant_modules " +
            "WHERE tenant_id = ? AND is_enabled = true",
            String.class, tenantId
        );
        return new HashSet<>(codes);
    }
}
