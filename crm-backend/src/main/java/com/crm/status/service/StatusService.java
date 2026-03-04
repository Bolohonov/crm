package com.crm.status.service;

import com.crm.common.exception.AppException;
import com.crm.status.dto.StatusDto;
import com.crm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatusService {

    private final JdbcTemplate jdbc;

    private static final Map<String, String> TABLE_MAP = Map.of(
            "orders", "order_statuses",
            "tasks",  "task_statuses"
    );

    public List<StatusDto.StatusResponse> list(String entityType) {
        String table  = resolveTable(entityType);
        String schema = TenantContext.get();

        return jdbc.queryForList(
                "SELECT id, code, name, color, sort_order, is_final, is_system " +
                        "FROM " + schema + "." + table +
                        " ORDER BY sort_order, name"
        ).stream().map(this::toResponse).toList();
    }

    public StatusDto.StatusResponse getById(String entityType, UUID id) {
        String table  = resolveTable(entityType);
        String schema = TenantContext.get();

        return jdbc.queryForList(
                        "SELECT id, code, name, color, sort_order, is_final, is_system " +
                                "FROM " + schema + "." + table + " WHERE id = ?", id
                ).stream().findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> AppException.notFound("Status"));
    }

    @Transactional
    public StatusDto.StatusResponse create(String entityType, StatusDto.CreateRequest req) {
        String table  = resolveTable(entityType);
        String schema = TenantContext.get();

        int exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + schema + "." + table + " WHERE code = ?",
                Integer.class, req.getCode()
        );
        if (exists > 0) {
            throw AppException.conflict("STATUS_CODE_EXISTS",
                    "Статус с кодом '" + req.getCode() + "' уже существует");
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO " + schema + "." + table +
                        " (id, code, name, color, sort_order, is_final, is_system) " +
                        "VALUES (?, ?, ?, ?, ?, ?, false)",
                id, req.getCode(), req.getName(),
                req.getColor() != null ? req.getColor() : "#6b7280",
                req.getSortOrder(), req.isFinal()
        );
        return getById(entityType, id);
    }

    @Transactional
    public StatusDto.StatusResponse update(String entityType, UUID id, StatusDto.UpdateRequest req) {
        String table  = resolveTable(entityType);
        String schema = TenantContext.get();

        Boolean isSystem = jdbc.queryForObject(
                "SELECT is_system FROM " + schema + "." + table + " WHERE id = ?",
                Boolean.class, id
        );
        if (Boolean.TRUE.equals(isSystem)) {
            throw AppException.badRequest("SYSTEM_STATUS",
                    "Системные статусы нельзя изменять. Можно поменять только название и цвет.");
        }

        jdbc.update(
                "UPDATE " + schema + "." + table +
                        " SET name = ?, color = ?, sort_order = ?, is_final = ? WHERE id = ?",
                req.getName(), req.getColor(), req.getSortOrder(), req.isFinal(), id
        );
        return getById(entityType, id);
    }

    @Transactional
    public StatusDto.StatusResponse patch(String entityType, UUID id, StatusDto.UpdateRequest req) {
        String table  = resolveTable(entityType);
        String schema = TenantContext.get();

        jdbc.update(
                "UPDATE " + schema + "." + table +
                        " SET name = ?, color = ? WHERE id = ?",
                req.getName(), req.getColor(), id
        );
        return getById(entityType, id);
    }

    @Transactional
    public void delete(String entityType, UUID id) {
        String table  = resolveTable(entityType);
        String schema = TenantContext.get();

        Boolean isSystem = jdbc.queryForObject(
                "SELECT is_system FROM " + schema + "." + table + " WHERE id = ?",
                Boolean.class, id
        );
        if (Boolean.TRUE.equals(isSystem)) {
            throw AppException.badRequest("SYSTEM_STATUS", "Системные статусы нельзя удалять");
        }

        String refTable = "orders".equals(entityType) ? "orders" : "tasks";
        int refs = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + schema + "." + refTable + " WHERE status_id = ?",
                Integer.class, id
        );
        if (refs > 0) {
            throw AppException.conflict("STATUS_IN_USE",
                    "Статус используется в " + refs + " записях и не может быть удалён");
        }

        jdbc.update("DELETE FROM " + schema + "." + table + " WHERE id = ?", id);
    }

    @Transactional
    public void reorder(String entityType, List<UUID> orderedIds) {
        String table  = resolveTable(entityType);
        String schema = TenantContext.get();

        for (int i = 0; i < orderedIds.size(); i++) {
            jdbc.update(
                    "UPDATE " + schema + "." + table +
                            " SET sort_order = ? WHERE id = ?",
                    i + 1, orderedIds.get(i)
            );
        }
    }

    private String resolveTable(String entityType) {
        String table = TABLE_MAP.get(entityType);
        if (table == null) {
            throw AppException.badRequest("INVALID_ENTITY_TYPE",
                    "Неверный тип сущности: " + entityType + ". Допустимые: orders, tasks");
        }
        return table;
    }

    @SuppressWarnings("unchecked")
    private StatusDto.StatusResponse toResponse(Map<String, Object> row) {
        return StatusDto.StatusResponse.builder()
                .id((UUID) row.get("id"))
                .code((String) row.get("code"))
                .name((String) row.get("name"))
                .color((String) row.get("color"))
                .sortOrder(row.get("sort_order") instanceof Number n ? n.intValue() : 0)
                .isFinal(Boolean.TRUE.equals(row.get("is_final")))
                .isSystem(Boolean.TRUE.equals(row.get("is_system")))
                .build();
    }
}