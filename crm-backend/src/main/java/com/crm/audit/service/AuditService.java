package com.crm.audit.service;

import com.crm.audit.dto.AuditDto;
import com.crm.audit.entity.AuditLog;
import com.crm.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Сервис аудита изменений.
 *
 * Пишет записи асинхронно (@Async) в отдельной транзакции —
 * ошибка записи аудита никогда не откатывает основную бизнес-операцию.
 *
 * Использование:
 * <pre>
 *   auditService.log("ORDER", orderId, "STATUS_CHANGED", actorId, actorName,
 *       Map.of("status", Map.of("before", "NEW", "after", "IN_PROGRESS")), null);
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;

    // ── Метки действий ────────────────────────────────────────────
    private static final Map<String, String> ACTION_LABELS = Map.of(
        "CREATED",        "Создано",
        "UPDATED",        "Обновлено",
        "STATUS_CHANGED", "Статус изменён",
        "DELETED",        "Удалено",
        "COMMENT_ADDED",  "Добавлен комментарий",
        "ASSIGNED",       "Назначен ответственный",
        "UNASSIGNED",     "Ответственный снят"
    );

    // ── Запись события ────────────────────────────────────────────

    /**
     * Асинхронная запись — не блокирует основной поток.
     * Если аудит не запишется (например, таблицы нет в dev-схеме) — просто логируем.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync(String entityType, UUID entityId, String action,
                         UUID actorId, String actorName,
                         Map<String, Object> changes, String comment) {
        try {
            repo.save(AuditLog.builder()
                .entityType(entityType.toUpperCase())
                .entityId(entityId)
                .action(action.toUpperCase())
                .actorId(actorId)
                .actorName(actorName)
                .changes(changes)
                .comment(comment)
                .createdAt(Instant.now())
                .build());
        } catch (Exception e) {
            log.warn("Audit write failed (non-critical): entityType={} entityId={} action={} error={}",
                entityType, entityId, action, e.getMessage());
        }
    }

    /**
     * Синхронная запись (для использования в той же транзакции, что и основная операция).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void log(String entityType, UUID entityId, String action,
                    UUID actorId, String actorName,
                    Map<String, Object> changes, String comment) {
        try {
            repo.save(AuditLog.builder()
                .entityType(entityType.toUpperCase())
                .entityId(entityId)
                .action(action.toUpperCase())
                .actorId(actorId)
                .actorName(actorName)
                .changes(changes)
                .comment(comment)
                .createdAt(Instant.now())
                .build());
        } catch (Exception e) {
            log.warn("Audit write failed (non-critical): {}", e.getMessage());
        }
    }

    // ── Удобные фабричные методы ──────────────────────────────────

    public void logStatusChange(String entityType, UUID entityId,
                                String fromCode, String toCode,
                                UUID actorId, String actorName, String comment) {
        logAsync(entityType, entityId, "STATUS_CHANGED", actorId, actorName,
            Map.of("status", Map.of("before", fromCode, "after", toCode)),
            comment);
    }

    public void logCreated(String entityType, UUID entityId,
                           UUID actorId, String actorName) {
        logAsync(entityType, entityId, "CREATED", actorId, actorName, Map.of(), null);
    }

    public void logUpdated(String entityType, UUID entityId,
                           UUID actorId, String actorName,
                           Map<String, Object> changes) {
        logAsync(entityType, entityId, "UPDATED", actorId, actorName, changes, null);
    }

    public void logComment(String entityType, UUID entityId,
                           UUID actorId, String actorName, String commentText) {
        logAsync(entityType, entityId, "COMMENT_ADDED", actorId, actorName, Map.of(), commentText);
    }

    // ── Чтение истории ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AuditDto.EntityTimelineResponse getTimeline(String entityType, UUID entityId) {
        List<AuditLog> entries =
            repo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType.toUpperCase(), entityId);

        return AuditDto.EntityTimelineResponse.builder()
            .entityId(entityId)
            .entityType(entityType)
            .timeline(entries.stream().map(this::toResponse).toList())
            .build();
    }

    @Transactional(readOnly = true)
    public List<AuditDto.AuditEntryResponse> getUserActivity(UUID actorId, int limit) {
        return repo.findRecentByActor(actorId, Math.min(limit, 50))
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditDto.AuditEntryResponse> getStatusHistory(String entityType, UUID entityId) {
        return repo.findStatusHistory(entityType.toUpperCase(), entityId)
            .stream().map(this::toResponse).toList();
    }

    // ── Маппинг ───────────────────────────────────────────────────

    private AuditDto.AuditEntryResponse toResponse(AuditLog e) {
        return AuditDto.AuditEntryResponse.builder()
            .id(e.getId())
            .entityType(e.getEntityType())
            .entityId(e.getEntityId())
            .action(e.getAction())
            .actionLabel(ACTION_LABELS.getOrDefault(e.getAction(), e.getAction()))
            .actorId(e.getActorId())
            .actorName(e.getActorName())
            .changes(e.getChanges())
            .comment(e.getComment())
            .createdAt(e.getCreatedAt())
            .build();
    }
}
