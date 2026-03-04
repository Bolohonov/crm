package com.crm.audit.controller;

import com.crm.audit.dto.AuditDto;
import com.crm.audit.service.AuditService;
import com.crm.common.response.ApiResponse;
import com.crm.rbac.config.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.crm.user.entity.User;

import java.util.List;
import java.util.UUID;

/**
 * Аудит и история изменений.
 *
 * GET /api/v1/audit/{entityType}/{entityId}/timeline  — таймлайн сущности
 * GET /api/v1/audit/{entityType}/{entityId}/status-history — история статусов
 * GET /api/v1/audit/my-activity                      — моя история действий
 * GET /api/v1/audit/users/{userId}/activity          — история действий пользователя [USER_VIEW]
 *
 * entityType: orders | tasks | customers
 */
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    // ── Таймлайн сущности (полная история) ───────────────────────
    @GetMapping("/{entityType}/{entityId}/timeline")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<AuditDto.EntityTimelineResponse>> getTimeline(
            @PathVariable String entityType,
            @PathVariable UUID entityId) {

        validateEntityType(entityType);
        return ResponseEntity.ok(ApiResponse.ok(
            auditService.getTimeline(entityType, entityId)
        ));
    }

    // ── История изменений статуса ─────────────────────────────────
    @GetMapping("/{entityType}/{entityId}/status-history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AuditDto.AuditEntryResponse>>> getStatusHistory(
            @PathVariable String entityType,
            @PathVariable UUID entityId) {

        validateEntityType(entityType);
        return ResponseEntity.ok(ApiResponse.ok(
            auditService.getStatusHistory(entityType, entityId)
        ));
    }

    // ── Моя активность ────────────────────────────────────────────
    @GetMapping("/my-activity")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<AuditDto.AuditEntryResponse>>> getMyActivity(
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal User currentUser) {

        return ResponseEntity.ok(ApiResponse.ok(
            auditService.getUserActivity(currentUser.getId(), limit)
        ));
    }

    // ── Активность конкретного пользователя (для Admin) ───────────
    @GetMapping("/users/{userId}/activity")
    @PreAuthorize("hasAuthority('" + Permissions.USER_VIEW + "')")
    public ResponseEntity<ApiResponse<List<AuditDto.AuditEntryResponse>>> getUserActivity(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "20") int limit) {

        return ResponseEntity.ok(ApiResponse.ok(
            auditService.getUserActivity(userId, limit)
        ));
    }

    // ── Валидация ─────────────────────────────────────────────────
    private void validateEntityType(String t) {
        if (!List.of("orders", "tasks", "customers", "users").contains(t)) {
            throw new IllegalArgumentException("Недопустимый тип сущности: " + t);
        }
    }
}
