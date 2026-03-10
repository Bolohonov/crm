package com.crm.status.controller;
import com.crm.status.dto.StatusUpdateRequest;
import com.crm.status.dto.StatusCreateRequest;

import com.crm.status.dto.StatusResponse;

import com.crm.common.response.ApiResponse;
import com.crm.rbac.config.Permissions;
import com.crm.status.service.StatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Справочник статусов заказов и задач.
 *
 * {entity} = orders | tasks
 *
 * GET    /api/v1/statuses/{entity}          — список статусов
 * GET    /api/v1/statuses/{entity}/{id}     — статус по ID
 * POST   /api/v1/statuses/{entity}          — создать статус [ROLE_MANAGE]
 * PUT    /api/v1/statuses/{entity}/{id}     — обновить статус [ROLE_MANAGE]
 * PATCH  /api/v1/statuses/{entity}/{id}     — обновить название/цвет (для системных) [ROLE_MANAGE]
 * DELETE /api/v1/statuses/{entity}/{id}     — удалить (не системный, не используется) [ROLE_MANAGE]
 * PUT    /api/v1/statuses/{entity}/reorder  — изменить порядок [ROLE_MANAGE]
 */
@RestController
@RequestMapping("/statuses/{entity}")
@RequiredArgsConstructor
public class StatusController {

    private final StatusService statusService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<StatusResponse>>> list(
            @PathVariable String entity) {

        return ResponseEntity.ok(ApiResponse.ok(statusService.list(entity)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StatusResponse>> getById(
            @PathVariable String entity,
            @PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.ok(statusService.getById(entity, id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_MANAGE + "')")
    public ResponseEntity<ApiResponse<StatusResponse>> create(
            @PathVariable String entity,
            @Valid @RequestBody StatusCreateRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(statusService.create(entity, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_MANAGE + "')")
    public ResponseEntity<ApiResponse<StatusResponse>> update(
            @PathVariable String entity,
            @PathVariable UUID id,
            @Valid @RequestBody StatusUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(statusService.update(entity, id, request)));
    }

    /**
     * PATCH — для системных статусов можно менять только name и color.
     * Для пользовательских работает как PUT.
     */
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_MANAGE + "')")
    public ResponseEntity<ApiResponse<StatusResponse>> patch(
            @PathVariable String entity,
            @PathVariable UUID id,
            @RequestBody StatusUpdateRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(statusService.patch(entity, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable String entity,
            @PathVariable UUID id) {

        statusService.delete(entity, id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PutMapping("/reorder")
    @PreAuthorize("hasAuthority('" + Permissions.ROLE_MANAGE + "')")
    public ResponseEntity<ApiResponse<Void>> reorder(
            @PathVariable String entity,
            @RequestBody List<UUID> orderedIds) {

        statusService.reorder(entity, orderedIds);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
