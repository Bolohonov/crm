package com.crm.rbac.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Настройки модулей тенанта из таблицы module_settings.
 * Таблица в схеме тенанта — search_path уже установлен.
 */
@Repository
@RequiredArgsConstructor
public class ModuleSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Возвращает коды включённых модулей.
     */
    public List<String> findEnabledModuleCodes() {
        return jdbcTemplate.queryForList(
            "SELECT module_code FROM module_settings WHERE is_enabled = true",
            String.class
        );
    }

    /**
     * Все модули с их статусом — для панели администратора.
     */
    public List<Map<String, Object>> findAllModules() {
        return jdbcTemplate.queryForList(
            "SELECT module_code, is_enabled FROM module_settings ORDER BY module_code"
        );
    }

    /**
     * Включает или отключает модуль.
     */
    public void setModuleEnabled(String moduleCode, boolean enabled) {
        jdbcTemplate.update(
            "UPDATE module_settings SET is_enabled = ?, updated_at = NOW() WHERE module_code = ?",
            enabled, moduleCode
        );
    }
}
