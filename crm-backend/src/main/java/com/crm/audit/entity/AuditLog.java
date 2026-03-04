package com.crm.audit.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("audit_log")
public class AuditLog {

    @Id
    private UUID id;

    @Column("entity_type")
    private String entityType;

    @Column("entity_id")
    private UUID entityId;

    @Column("action")
    private String action;

    @Column("actor_id")
    private UUID actorId;

    @Column("actor_name")
    private String actorName;

    @Column("changes")
    private String changes;

    @Column("comment")
    private String comment;

    @Column("created_at")
    private Instant createdAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditLog a)) return false;
        return java.util.Objects.equals(id, a.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hashCode(id);
    }
}