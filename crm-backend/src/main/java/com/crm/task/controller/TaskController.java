package com.crm.task.controller;
import com.crm.task.dto.TaskPageResponse;
import com.crm.task.dto.TaskChangeStatusRequest;
import com.crm.task.dto.TaskFilterRequest;
import com.crm.task.dto.TaskUpdateRequest;
import com.crm.task.dto.TaskCreateRequest;

import com.crm.task.dto.CalendarEvent;
import com.crm.task.dto.CalendarRequest;
import com.crm.task.dto.CommentRequest;
import com.crm.task.dto.CommentResponse;
import com.crm.task.dto.TaskResponse;

import com.crm.common.response.ApiResponse;
import com.crm.task.service.TaskService;
import com.crm.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * GET    /tasks                          — список задач с фильтрами
 * GET    /tasks/today                    — задачи на сегодня
 * GET    /tasks/calendar?from=&to=       — события для календаря
 * GET    /tasks/{id}                     — карточка задачи
 * POST   /tasks                          — создать задачу
 * PUT    /tasks/{id}                     — обновить задачу
 * PATCH  /tasks/{id}/status              — изменить статус
 * PATCH  /tasks/{id}/assign              — переназначить
 * DELETE /tasks/{id}                     — удалить
 * GET    /tasks/{id}/comments            — комментарии
 * POST   /tasks/{id}/comments            — добавить комментарий
 * DELETE /tasks/{id}/comments/{commentId} — удалить комментарий
 */
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<ApiResponse<TaskPageResponse>> list(
            @RequestParam(required = false) UUID assigneeId,
            @RequestParam(required = false) UUID statusId,
            @RequestParam(required = false) UUID typeId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        var req = new TaskFilterRequest();
        req.setAssigneeId(assigneeId); req.setStatusId(statusId);
        req.setTypeId(typeId);         req.setCustomerId(customerId);
        req.setPage(page);             req.setSize(size);

        return ResponseEntity.ok(ApiResponse.ok(taskService.list(req)));
    }

    @GetMapping("/today")
    public ResponseEntity<ApiResponse<List<TaskResponse>>> today(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) UUID assigneeId) {

        UUID targetUser = assigneeId != null ? assigneeId : user.getId();
        return ResponseEntity.ok(ApiResponse.ok(taskService.getToday(targetUser)));
    }

    @GetMapping("/calendar")
    public ResponseEntity<ApiResponse<List<CalendarEvent>>> calendar(
            @RequestParam Instant from,
            @RequestParam Instant to,
            @RequestParam(required = false) UUID assigneeId) {

        var req = new CalendarRequest();
        req.setFrom(from); req.setTo(to); req.setAssigneeId(assigneeId);
        return ResponseEntity.ok(ApiResponse.ok(taskService.getCalendar(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskResponse>> create(
            @Valid @RequestBody TaskCreateRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(taskService.create(request, user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<TaskResponse>> update(
            @PathVariable UUID id,
            @Valid @RequestBody TaskUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Void>> changeStatus(
            @PathVariable UUID id,
            @RequestBody TaskChangeStatusRequest request,
            @AuthenticationPrincipal User currentUser) {
        taskService.changeStatus(id, request.getStatusId(), currentUser, request.getComment());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @GetMapping("/{id}/allowed-transitions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<java.util.Set<String>>> allowedTransitions(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser) {
        boolean isAdmin = "ADMIN".equals(
            currentUser.getUserType() != null ? currentUser.getUserType().name() : "");
        return ResponseEntity.ok(ApiResponse.ok(
            taskService.allowedTransitions(id, isAdmin)));
    }

    @PatchMapping("/{id}/assign")
    public ResponseEntity<ApiResponse<Void>> assign(
            @PathVariable UUID id,
            @RequestParam UUID assigneeId) {
        taskService.assign(id, assigneeId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        taskService.delete(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── Комментарии ──

    @GetMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(taskService.getComments(id)));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody CommentRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok(taskService.addComment(id, request, user)));
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable UUID id,
            @PathVariable UUID commentId,
            @AuthenticationPrincipal User user) {
        taskService.deleteComment(commentId, user.getId());
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
