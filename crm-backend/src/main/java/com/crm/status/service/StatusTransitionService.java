package com.crm.status.service;

import com.crm.common.exception.AppException;
import com.crm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Валидация переходов между статусами.
 *
 * Бизнес-правила:
 *
 * Заказы (ORDER):
 *   NEW         → IN_PROGRESS, CANCELLED
 *   IN_PROGRESS → WAITING, DONE, CANCELLED
 *   WAITING     → IN_PROGRESS, DONE, CANCELLED
 *   DONE        → (финальный — переход запрещён без прав ROLE_MANAGE)
 *   CANCELLED   → (финальный — переход запрещён без прав ROLE_MANAGE)
 *
 * Задачи (TASK):
 *   TODO        → IN_PROGRESS, CANCELLED
 *   IN_PROGRESS → DONE, CANCELLED, TODO (откат)
 *   DONE        → (финальный — переход запрещён без прав ROLE_MANAGE)
 *   CANCELLED   → (финальный — переход запрещён без прав ROLE_MANAGE)
 *
 * Пользовательские статусы: переход всегда разрешён между не-финальными.
 * Из финального — только при isAdmin.
 *
 * Использование:
 *   transitionService.validate("orders", fromCode, toCode, isAdmin);
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatusTransitionService {

    private final JdbcTemplate jdbc;

    // ── Допустимые переходы для заказов ──────────────────────────
    private static final Map<String, Set<String>> ORDER_TRANSITIONS = Map.of(
        "NEW",         Set.of("IN_PROGRESS", "CANCELLED"),
        "IN_PROGRESS", Set.of("WAITING", "DONE", "CANCELLED"),
        "WAITING",     Set.of("IN_PROGRESS", "DONE", "CANCELLED"),
        "DONE",        Set.of(),        // финальный
        "CANCELLED",   Set.of()         // финальный
    );

    // ── Допустимые переходы для задач ────────────────────────────
    private static final Map<String, Set<String>> TASK_TRANSITIONS = Map.of(
        "TODO",        Set.of("IN_PROGRESS", "CANCELLED"),
        "IN_PROGRESS", Set.of("TODO", "DONE", "CANCELLED"),
        "DONE",        Set.of(),        // финальный
        "CANCELLED",   Set.of()         // финальный
    );

    /**
     * Проверяет допустимость перехода статуса.
     *
     * @param entityType  "orders" или "tasks"
     * @param fromCode    код текущего статуса
     * @param toCode      код целевого статуса
     * @param isAdmin     администратор может обходить финальные ограничения
     * @throws AppException если переход недопустим
     */
    public void validate(String entityType, String fromCode, String toCode, boolean isAdmin) {
        if (fromCode.equals(toCode)) return; // переход в тот же статус — нейтрально

        Map<String, Set<String>> matrix = resolveMatrix(entityType);

        Set<String> allowed = matrix.get(fromCode.toUpperCase());

        if (allowed == null) {
            // Пользовательский статус — проверяем is_final
            boolean isFinal = checkIsFinal(entityType, fromCode);
            if (isFinal && !isAdmin) {
                throw AppException.badRequest("STATUS_FINAL",
                    "Статус '%s' является финальным. Смена возможна только администратором.".formatted(fromCode));
            }
            return; // пользовательские статусы без явной матрицы — разрешены (не финальные)
        }

        if (allowed.isEmpty() && !isAdmin) {
            throw AppException.badRequest("STATUS_FINAL",
                "Статус '%s' является финальным и не может быть изменён.".formatted(fromCode) +
                " Для принудительной смены обратитесь к администратору.");
        }

        if (!allowed.isEmpty() && !allowed.contains(toCode.toUpperCase()) && !isAdmin) {
            throw AppException.badRequest("INVALID_TRANSITION",
                "Переход из статуса '%s' в '%s' недопустим.".formatted(fromCode, toCode) +
                " Разрешённые переходы: " + String.join(", ", allowed));
        }
    }

    /**
     * Возвращает список кодов статусов, в которые можно перейти из данного.
     * Удобно для UI — подсвечивать доступные кнопки.
     */
    public Set<String> allowedTargets(String entityType, String fromCode, boolean isAdmin) {
        Map<String, Set<String>> matrix = resolveMatrix(entityType);
        Set<String> allowed = matrix.get(fromCode.toUpperCase());

        if (allowed == null) return loadAllNonFinalCodes(entityType); // пользовательский статус
        if (allowed.isEmpty() && isAdmin) return loadAllCodes(entityType);
        if (allowed.isEmpty()) return Set.of();
        return Collections.unmodifiableSet(allowed);
    }

    // ── Утилиты ───────────────────────────────────────────────────

    private Map<String, Set<String>> resolveMatrix(String entityType) {
        return switch (entityType.toLowerCase()) {
            case "orders" -> ORDER_TRANSITIONS;
            case "tasks"  -> TASK_TRANSITIONS;
            default -> throw AppException.badRequest("INVALID_ENTITY", "Неверный тип: " + entityType);
        };
    }

    private boolean checkIsFinal(String entityType, String code) {
        String table  = "orders".equals(entityType) ? "order_statuses" : "task_statuses";
        String schema = TenantContext.get();
        Boolean isFinal = jdbc.queryForObject(
            "SELECT is_final FROM " + schema + "." + table + " WHERE code = ?",
            Boolean.class, code.toUpperCase()
        );
        return Boolean.TRUE.equals(isFinal);
    }

    private Set<String> loadAllCodes(String entityType) {
        String table  = "orders".equals(entityType) ? "order_statuses" : "task_statuses";
        String schema = TenantContext.get();
        return new HashSet<>(jdbc.queryForList(
            "SELECT code FROM " + schema + "." + table, String.class
        ));
    }

    private Set<String> loadAllNonFinalCodes(String entityType) {
        String table  = "orders".equals(entityType) ? "order_statuses" : "task_statuses";
        String schema = TenantContext.get();
        return new HashSet<>(jdbc.queryForList(
            "SELECT code FROM " + schema + "." + table + " WHERE is_final = false", String.class
        ));
    }
}
