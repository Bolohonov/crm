package com.crm.audit.repository;

import com.crm.audit.entity.AuditLog;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends CrudRepository<AuditLog, UUID> {

    /** История конкретной сущности — для таймлайна в карточке заказа/задачи */
    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(
        String entityType, UUID entityId
    );

    /** Последние N записей по актору (история действий пользователя) */
    @Query("SELECT a FROM AuditLog a WHERE a.actorId = :actorId ORDER BY a.createdAt DESC LIMIT :limit")
    List<AuditLog> findRecentByActor(@Param("actorId") UUID actorId, @Param("limit") int limit);

    /** Последние изменения статусов для дашборда активности */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.action IN ('STATUS_CHANGED', 'CREATED', 'COMMENT_ADDED')
        ORDER BY a.createdAt DESC
        LIMIT :limit
    """)
    List<AuditLog> findRecentActivity(@Param("limit") int limit);

    /** История изменений статусов конкретной сущности */
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.entityType = :entityType AND a.entityId = :entityId
          AND a.action = 'STATUS_CHANGED'
        ORDER BY a.createdAt DESC
    """)
    List<AuditLog> findStatusHistory(
        @Param("entityType") String entityType,
        @Param("entityId") UUID entityId
    );
}
