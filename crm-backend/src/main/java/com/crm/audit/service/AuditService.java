package com.crm.audit.service;

import com.crm.audit.dto.AuditEntryResponse;
import com.crm.audit.dto.EntityTimelineResponse;

import com.crm.audit.entity.AuditLog;
import com.crm.audit.repository.AuditLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repo;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    private static final Map<String, String> ACTION_LABELS = Map.of(
        "CREATED",        "Создано",
        "UPDATED",        "Обновлено",
        "STATUS_CHANGED", "Статус изменён",
        "DELETED",        "Удалено",
        "COMMENT_ADDED",  "Добавлен комментарий",
        "ASSIGNED",       "Назначен ответственный",
        "UNASSIGNED",     "Ответственный снят"
    );

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAsync(String entityType, UUID entityId, String action,
                         UUID actorId, String actorName,
                         Map<String, Object> changes, String comment) {
        try {
            jdbc.update(
                    "INSERT INTO audit_log (id, entity_type, entity_id, action, actor_id, actor_name, changes, comment, created_at) " +
                            "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?::jsonb, ?, NOW())",
                    entityType.toUpperCase(),
                    entityId,
                    action.toUpperCase(),
                    actorId,
                    actorName,
                    toJson(changes),
                    comment
            );
        } catch (Exception e) {
            log.warn("Audit write failed (non-critical): entityType={} entityId={} action={} error={}",
                    entityType, entityId, action, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void log(String entityType, UUID entityId, String action,
                    UUID actorId, String actorName,
                    Map<String, Object> changes, String comment) {
        try {
            jdbc.update(
                    "INSERT INTO audit_log (id, entity_type, entity_id, action, actor_id, actor_name, changes, comment, created_at) " +
                            "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?::jsonb, ?, NOW())",
                    entityType.toUpperCase(),
                    entityId,
                    action.toUpperCase(),
                    actorId,
                    actorName,
                    toJson(changes),
                    comment
            );
        } catch (Exception e) {
            log.warn("Audit write failed (non-critical): {}", e.getMessage());
        }
    }

    // ── Фабричные методы ─────────────────────────────────────────

    public void logStatusChange(String entityType, UUID entityId,
                                String fromCode, String toCode,
                                UUID actorId, String actorName, String comment) {
        logAsync(entityType, entityId, "STATUS_CHANGED", actorId, actorName,
            Map.of("status", Map.of("before", fromCode, "after", toCode)),
            comment);
    }

    public void logCreated(String entityType, UUID entityId, UUID actorId, String actorName) {
        logAsync(entityType, entityId, "CREATED", actorId, actorName, Map.of(), null);
    }

    public void logUpdated(String entityType, UUID entityId,
                           UUID actorId, String actorName, Map<String, Object> changes) {
        logAsync(entityType, entityId, "UPDATED", actorId, actorName, changes, null);
    }

    public void logComment(String entityType, UUID entityId,
                           UUID actorId, String actorName, String commentText) {
        logAsync(entityType, entityId, "COMMENT_ADDED", actorId, actorName, Map.of(), commentText);
    }

    // ── Чтение ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public EntityTimelineResponse getTimeline(String entityType, UUID entityId) {
        List<AuditLog> entries =
            repo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType.toUpperCase(), entityId);
        return EntityTimelineResponse.builder()
            .entityId(entityId)
            .entityType(entityType)
            .timeline(entries.stream().map(this::toResponse).toList())
            .build();
    }

    @Transactional(readOnly = true)
    public List<AuditEntryResponse> getUserActivity(UUID actorId, int limit) {
        return repo.findRecentByActor(actorId, Math.min(limit, 50))
            .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AuditEntryResponse> getStatusHistory(String entityType, UUID entityId) {
        return repo.findStatusHistory(entityType.toUpperCase(), entityId)
            .stream().map(this::toResponse).toList();
    }

    // ── Маппинг ───────────────────────────────────────────────────

    private AuditEntryResponse toResponse(AuditLog e) {
        return AuditEntryResponse.builder()
            .id(e.getId())
            .entityType(e.getEntityType())
            .entityId(e.getEntityId())
            .action(e.getAction())
            .actionLabel(ACTION_LABELS.getOrDefault(e.getAction(), e.getAction()))
            .actorId(e.getActorId())
            .actorName(e.getActorName())
            .changes(fromJson(e.getChanges()))
            .comment(e.getComment())
            .createdAt(e.getCreatedAt())
            .build();
    }

    private String toJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) return "{}";
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}
