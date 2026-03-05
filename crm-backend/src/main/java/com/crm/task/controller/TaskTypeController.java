package com.crm.task.controller;

import com.crm.common.response.ApiResponse;
import com.crm.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Справочник типов задач.
 *
 * GET  /api/v1/task-types        — список типов задач тенанта
 * POST /api/v1/task-types        — создать тип задачи
 */
@RestController
@RequestMapping("/task-types")
@RequiredArgsConstructor
public class TaskTypeController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> list() {
        String schema = TenantContext.get();

        List<Map<String, Object>> types = jdbc.queryForList(
            "SELECT id, code, name, color, sort_order " +
            "FROM " + schema + ".task_types " +
            "ORDER BY sort_order, name"
        );

        return ResponseEntity.ok(ApiResponse.ok(types));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> create(
            @RequestBody Map<String, Object> req) {

        String schema = TenantContext.get();
        UUID id = UUID.randomUUID();

        String code  = (String) req.getOrDefault("code",  id.toString().substring(0, 8));
        String name  = (String) req.getOrDefault("name",  "Задача");
        String color = (String) req.getOrDefault("color", "#6b7280");
        int    sort  = req.get("sortOrder") instanceof Number n ? n.intValue() : 99;

        jdbc.update(
            "INSERT INTO " + schema + ".task_types (id, code, name, color, sort_order) " +
            "VALUES (?, ?, ?, ?, ?)",
            id, code, name, color, sort
        );

        Map<String, Object> result = jdbc.queryForMap(
            "SELECT id, code, name, color, sort_order FROM " + schema + ".task_types WHERE id = ?",
            id
        );

        return ResponseEntity.status(201).body(ApiResponse.ok(result));
    }
}
