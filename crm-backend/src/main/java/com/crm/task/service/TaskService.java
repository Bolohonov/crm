package com.crm.task.service;

import com.crm.audit.service.AuditService;
import com.crm.common.exception.AppException;
import com.crm.rbac.config.Permissions;
import com.crm.status.service.StatusTransitionService;
import com.crm.task.dto.TaskDto;
import com.crm.task.entity.Task;
import com.crm.task.entity.TaskComment;
import com.crm.task.repository.TaskCommentRepository;
import com.crm.task.repository.TaskRepository;
import com.crm.tenant.TenantContext;
import com.crm.user.entity.User;
import com.crm.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository        taskRepository;
    private final TaskCommentRepository commentRepository;
    private final UserRepository        userRepository;
    private final JdbcTemplate          jdbc;
    private final AuditService          auditService;
    private final StatusTransitionService transitionService;

    // ── Список с пагинацией ──────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.TASK_VIEW + "')")
    public TaskDto.PageResponse list(TaskDto.FilterRequest req) {
        int size   = Math.min(req.getSize(), 100);
        int offset = req.getPage() * size;

        List<Task> tasks = taskRepository.findAll(
            uuid(req.getAssigneeId()), uuid(req.getStatusId()),
            uuid(req.getTypeId()),    uuid(req.getCustomerId()),
            size, offset
        );
        long total = taskRepository.countAll(
            uuid(req.getAssigneeId()), uuid(req.getStatusId()),
            uuid(req.getTypeId()),    uuid(req.getCustomerId())
        );

        return TaskDto.PageResponse.builder()
            .content(tasks.stream().map(t -> toResponse(t, false)).toList())
            .totalElements(total)
            .totalPages((int) Math.ceil((double) total / size))
            .page(req.getPage()).size(size)
            .build();
    }

    // ── Детальная карточка ───────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.TASK_VIEW + "')")
    public TaskDto.TaskResponse getById(UUID id) {
        Task task = find(id);
        return toResponse(task, true);
    }

    // ── Задачи на сегодня (для дашборда) ────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.TASK_VIEW + "')")
    public List<TaskDto.TaskResponse> getToday(UUID userId) {
        ZonedDateTime startOfDay = LocalDate.now(ZoneId.of("Europe/Moscow"))
            .atStartOfDay(ZoneId.of("Europe/Moscow"));
        Instant from = startOfDay.toInstant();
        Instant to   = startOfDay.plusDays(1).toInstant();

        return taskRepository.findTodayTasks(from, to, uuid(userId))
            .stream().map(t -> toResponse(t, false)).toList();
    }

    // ── Календарь ────────────────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.TASK_VIEW + "')")
    public List<TaskDto.CalendarEvent> getCalendar(TaskDto.CalendarRequest req) {
        List<Task> tasks = taskRepository.findByDateRange(
            req.getFrom(), req.getTo(), uuid(req.getAssigneeId())
        );

        Instant now = Instant.now();
        return tasks.stream().map(t -> {
            // Получаем данные статуса и типа
            var statusRow = queryStatusById(t.getStatusId());
            var typeColor = queryTypeColor(t.getTaskTypeId());
            String assigneeName = t.getAssigneeId() != null
                ? getUserName(t.getAssigneeId()) : null;

            return TaskDto.CalendarEvent.builder()
                .id(t.getId())
                .title(t.getTitle())
                .scheduledAt(t.getScheduledAt())
                .completedAt(t.getCompletedAt())
                .statusCode((String) statusRow.getOrDefault("code", ""))
                .statusColor((String) statusRow.getOrDefault("color", "#888"))
                .typeColor(typeColor)
                .assigneeId(t.getAssigneeId())
                .assigneeName(assigneeName)
                .overdue(t.getCompletedAt() == null
                    && t.getScheduledAt() != null
                    && t.getScheduledAt().isBefore(now))
                .build();
        }).toList();
    }

    // ── Создание ─────────────────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.TASK_CREATE + "')")
    @Transactional
    public TaskDto.TaskResponse create(TaskDto.CreateRequest req, User author) {
        Task task = Task.builder()
            .title(req.getTitle())
            .description(req.getDescription())
            .taskTypeId(req.getTaskTypeId())
            .statusId(req.getStatusId())
            .authorId(author.getId())
            .assigneeId(req.getAssigneeId())
            .customerId(req.getCustomerId())
            .scheduledAt(req.getScheduledAt())
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        task = taskRepository.save(task);
        log.info("Task created: {} by user {}", task.getId(), author.getId());
        return toResponse(task, false);
    }

    // ── Обновление ───────────────────────────────────────────────────

    @PreAuthorize("@sec.isOwnerOrHas(#taskId, '" + Permissions.TASK_EDIT + "')")
    @Transactional
    public TaskDto.TaskResponse update(UUID taskId, TaskDto.UpdateRequest req) {
        Task task = find(taskId);

        if (req.getTitle()       != null) task.setTitle(req.getTitle());
        if (req.getDescription() != null) task.setDescription(req.getDescription());
        if (req.getTaskTypeId()  != null) task.setTaskTypeId(req.getTaskTypeId());
        if (req.getAssigneeId()  != null) task.setAssigneeId(req.getAssigneeId());
        if (req.getCustomerId()  != null) task.setCustomerId(req.getCustomerId());
        if (req.getScheduledAt() != null) task.setScheduledAt(req.getScheduledAt());
        if (req.getStatusId()    != null) {
            task.setStatusId(req.getStatusId());
            // Если переводим в DONE — фиксируем время
            String code = queryStatusCode(req.getStatusId());
            if ("DONE".equals(code)) task.setCompletedAt(Instant.now());
        }

        task.setUpdatedAt(Instant.now());
        task = taskRepository.save(task);
        return toResponse(task, false);
    }

    // ── Быстрая смена статуса ────────────────────────────────────────

    @Transactional
    @PreAuthorize("@sec.isOwnerOrHas(#taskId, '" + Permissions.TASK_EDIT + "')")
    public void changeStatus(UUID taskId, UUID newStatusId, User actor, String comment) {
        if (!taskRepository.existsById(taskId)) throw AppException.notFound("Задача");

        // Загружаем коды текущего и нового статуса
        String schema = com.crm.tenant.TenantContext.get();
        String fromCode = jdbc.queryForObject(
            "SELECT ts.code FROM " + schema + ".tasks t " +
            "JOIN " + schema + ".task_statuses ts ON ts.id = t.status_id WHERE t.id = ?",
            String.class, taskId);
        String toCode = jdbc.queryForObject(
            "SELECT code FROM " + schema + ".task_statuses WHERE id = ?",
            String.class, newStatusId);

        boolean isAdmin = actor != null && actor.getUserType() != null &&
            "ADMIN".equals(actor.getUserType().name());
        transitionService.validate("tasks", fromCode, toCode, isAdmin);

        taskRepository.updateStatus(taskId, newStatusId);

        // Если финальный — ставим completed_at
        Boolean isFinal = jdbc.queryForObject(
            "SELECT is_final FROM " + schema + ".task_statuses WHERE id = ?",
            Boolean.class, newStatusId);
        if (Boolean.TRUE.equals(isFinal) && "DONE".equals(toCode)) {
            jdbc.update("UPDATE " + schema + ".tasks SET completed_at = NOW() WHERE id = ?", taskId);
        } else if (!Boolean.TRUE.equals(isFinal)) {
            jdbc.update("UPDATE " + schema + ".tasks SET completed_at = NULL WHERE id = ?", taskId);
        }

        UUID actorId = actor != null ? actor.getId() : null;
        String actorName = actor != null
            ? actor.getLastName() + " " + actor.getFirstName().charAt(0) + "."
            : "system";
        auditService.logStatusChange("TASK", taskId, fromCode, toCode, actorId, actorName, comment);
    }

    /** Обратная совместимость */
    @Transactional
    @PreAuthorize("@sec.isOwnerOrHas(#taskId, '" + Permissions.TASK_EDIT + "')")
    public void changeStatus(UUID taskId, UUID newStatusId) {
        changeStatus(taskId, newStatusId, null, null);
    }

    /** Допустимые переходы для UI */
    public Set<String> allowedTransitions(UUID taskId, boolean isAdmin) {
        String schema = com.crm.tenant.TenantContext.get();
        String currentCode = jdbc.queryForObject(
            "SELECT ts.code FROM " + schema + ".tasks t " +
            "JOIN " + schema + ".task_statuses ts ON ts.id = t.status_id WHERE t.id = ?",
            String.class, taskId);
        return transitionService.allowedTargets("tasks", currentCode, isAdmin);
    }

    // ── Переназначение ───────────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.TASK_ASSIGN + "')")
    public void assign(UUID taskId, UUID assigneeId) {
        if (!taskRepository.existsById(taskId)) throw AppException.notFound("Задача");
        taskRepository.updateAssignee(taskId, assigneeId);
    }

    // ── Удаление ─────────────────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.TASK_DELETE + "')")
    @Transactional
    public void delete(UUID id) {
        if (!taskRepository.existsById(id)) throw AppException.notFound("Задача");
        taskRepository.deleteById(id);
    }

    // ── Комментарии ──────────────────────────────────────────────────

    @PreAuthorize("@sec.has('" + Permissions.TASK_VIEW + "')")
    public List<TaskDto.CommentResponse> getComments(UUID taskId) {
        return commentRepository.findByTaskId(taskId)
            .stream().map(this::toCommentResponse).toList();
    }

    @PreAuthorize("@sec.has('" + Permissions.TASK_VIEW + "')")
    @Transactional
    public TaskDto.CommentResponse addComment(UUID taskId, TaskDto.CommentRequest req, User author) {
        if (!taskRepository.existsById(taskId)) throw AppException.notFound("Задача");
        TaskComment comment = TaskComment.builder()
            .taskId(taskId).authorId(author.getId())
            .content(req.getContent())
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        return toCommentResponse(commentRepository.save(comment));
    }

    @PreAuthorize("@sec.isOwner(#authorId)")
    public void deleteComment(UUID commentId, UUID authorId) {
        commentRepository.deleteByIdAndAuthorId(commentId, authorId);
    }

    // ── Маппинг ──────────────────────────────────────────────────────

    private TaskDto.TaskResponse toResponse(Task t, boolean withComments) {
        Instant now = Instant.now();
        boolean overdue = t.getCompletedAt() == null
            && t.getScheduledAt() != null
            && t.getScheduledAt().isBefore(now);

        LocalDate today = LocalDate.now(ZoneId.of("Europe/Moscow"));
        boolean dueToday = t.getScheduledAt() != null
            && LocalDate.ofInstant(t.getScheduledAt(), ZoneId.of("Europe/Moscow")).equals(today);

        var statusRow = queryStatusById(t.getStatusId());

        var builder = TaskDto.TaskResponse.builder()
            .id(t.getId())
            .title(t.getTitle())
            .description(t.getDescription())
            .taskTypeId(t.getTaskTypeId())
            .statusId(t.getStatusId())
            .statusName((String)  statusRow.getOrDefault("name",  ""))
            .statusCode((String)  statusRow.getOrDefault("code",  ""))
            .statusColor((String) statusRow.getOrDefault("color", "#888"))
            .authorId(t.getAuthorId())
            .authorName(t.getAuthorId() != null ? getUserName(t.getAuthorId()) : null)
            .assigneeId(t.getAssigneeId())
            .assigneeName(t.getAssigneeId() != null ? getUserName(t.getAssigneeId()) : null)
            .customerId(t.getCustomerId())
            .scheduledAt(t.getScheduledAt())
            .completedAt(t.getCompletedAt())
            .createdAt(t.getCreatedAt())
            .updatedAt(t.getUpdatedAt())
            .overdue(overdue)
            .dueToday(dueToday);

        if (withComments) {
            builder.comments(
                commentRepository.findByTaskId(t.getId())
                    .stream().map(this::toCommentResponse).toList()
            );
        }

        return builder.build();
    }

    private TaskDto.CommentResponse toCommentResponse(TaskComment c) {
        return TaskDto.CommentResponse.builder()
            .id(c.getId()).authorId(c.getAuthorId())
            .authorName(getUserName(c.getAuthorId()))
            .content(c.getContent())
            .createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt())
            .build();
    }

    // ── Приватные хелперы ─────────────────────────────────────────────

    private Task find(UUID id) {
        return taskRepository.findById(id).orElseThrow(() -> AppException.notFound("Задача"));
    }

    private String uuid(UUID id) { return id != null ? id.toString() : null; }

    // Получение имени пользователя (можно кэшировать в Redis — TODO)
    private final Map<UUID, String> userNameCache = new HashMap<>();
    private String getUserName(UUID userId) {
        return userNameCache.computeIfAbsent(userId, id -> {
            try {
                return jdbc.queryForObject(
                    "SELECT last_name || ' ' || first_name FROM public.users WHERE id = ?",
                    String.class, id
                );
            } catch (Exception e) { return "—"; }
        });
    }

    private Map<String, Object> queryStatusById(UUID statusId) {
        if (statusId == null) return Map.of();
        String schema = TenantContext.get();
        try {
            return jdbc.queryForMap(
                "SELECT code, name, color FROM " + schema + ".task_statuses WHERE id = ?", statusId
            );
        } catch (Exception e) { return Map.of(); }
    }

    private String queryStatusCode(UUID statusId) {
        if (statusId == null) return "";
        String schema = TenantContext.get();
        try {
            return jdbc.queryForObject(
                "SELECT code FROM " + schema + ".task_statuses WHERE id = ?", String.class, statusId
            );
        } catch (Exception e) { return ""; }
    }

    private String queryTypeColor(UUID typeId) {
        if (typeId == null) return "#888";
        String schema = TenantContext.get();
        try {
            return jdbc.queryForObject(
                "SELECT color FROM " + schema + ".task_types WHERE id = ?", String.class, typeId
            );
        } catch (Exception e) { return "#888"; }
    }
}
