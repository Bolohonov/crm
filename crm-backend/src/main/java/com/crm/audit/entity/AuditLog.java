package com.crm.audit.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Аудит-запись изменения любой сущности в тенанте.
 *
 * entityType: ORDER, TASK, CUSTOMER, USER, ROLE, MODULE
 * action:     CREATED, UPDATED, STATUS_CHANGED, DELETED, COMMENT_ADDED, ASSIGNED
 *
 * changes хранит JSON-diff { field: { before, after } }
 */
@Entity
@Table(name = "audit_log")                   // таблица в схеме тенанта (через TenantAwareDataSource)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;                // ORDER, TASK, CUSTOMER ...

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Column(name = "action", nullable = false, length = 50)
    private String action;                    // CREATED, STATUS_CHANGED, UPDATED ...

    @Column(name = "actor_id")
    private UUID actorId;                     // кто изменил (null = система)

    @Column(name = "actor_name", length = 200)
    private String actorName;                 // имя для отображения (денормализация)

    /** JSON: { "status": { "before": "NEW", "after": "IN_PROGRESS" } } */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "changes", columnDefinition = "jsonb")
    private Map<String, Object> changes;

    @Column(name = "comment", length = 2000)
    private String comment;                   // опциональный комментарий к изменению

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
