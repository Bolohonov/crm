package com.crm.task.repository;

import com.crm.task.entity.Task;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends CrudRepository<Task, UUID> {

    // ── Список с фильтрами и пагинацией ──────────────────────────────

    @Query("""
        SELECT t.*
        FROM tasks t
        WHERE (:assigneeId IS NULL OR t.assignee_id = :assigneeId::uuid)
          AND (:statusId IS NULL OR t.status_id = :statusId::uuid)
          AND (:typeId IS NULL OR t.task_type_id = :typeId::uuid)
          AND (:customerId IS NULL OR t.customer_id = :customerId::uuid)
        ORDER BY
          CASE WHEN t.scheduled_at IS NULL THEN 1 ELSE 0 END,
          t.scheduled_at ASC,
          t.created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    List<Task> findAll(String assigneeId, String statusId, String typeId,
                       String customerId, int limit, int offset);

    @Query("""
        SELECT COUNT(*)
        FROM tasks t
        WHERE (:assigneeId IS NULL OR t.assignee_id = :assigneeId::uuid)
          AND (:statusId IS NULL OR t.status_id = :statusId::uuid)
          AND (:typeId IS NULL OR t.task_type_id = :typeId::uuid)
          AND (:customerId IS NULL OR t.customer_id = :customerId::uuid)
        """)
    long countAll(String assigneeId, String statusId, String typeId, String customerId);

    // ── Календарь: задачи в диапазоне дат ────────────────────────────

    @Query("""
        SELECT t.*
        FROM tasks t
        WHERE t.scheduled_at >= :from
          AND t.scheduled_at <  :to
          AND (:assigneeId IS NULL OR t.assignee_id = :assigneeId::uuid)
        ORDER BY t.scheduled_at ASC
        """)
    List<Task> findByDateRange(Instant from, Instant to, String assigneeId);

    // ── Задачи "на сегодня" для дашборда ─────────────────────────────

    @Query("""
        SELECT t.*
        FROM tasks t
        WHERE t.scheduled_at >= :dayStart
          AND t.scheduled_at <  :dayEnd
          AND t.completed_at IS NULL
          AND (:userId IS NULL OR t.assignee_id = :userId::uuid OR t.author_id = :userId::uuid)
        ORDER BY t.scheduled_at ASC
        LIMIT 20
        """)
    List<Task> findTodayTasks(Instant dayStart, Instant dayEnd, String userId);

    // ── Просроченные задачи ───────────────────────────────────────────

    @Query("""
        SELECT t.*
        FROM tasks t
        JOIN task_statuses ts ON ts.id = t.status_id
        WHERE t.scheduled_at < :now
          AND t.completed_at IS NULL
          AND ts.code NOT IN ('DONE', 'CANCELLED')
          AND (:assigneeId IS NULL OR t.assignee_id = :assigneeId::uuid)
        ORDER BY t.scheduled_at ASC
        LIMIT :limit
        """)
    List<Task> findOverdue(Instant now, String assigneeId, int limit);

    // ── Задачи по клиенту ─────────────────────────────────────────────

    @Query("""
        SELECT t.* FROM tasks t
        WHERE t.customer_id = :customerId
        ORDER BY t.created_at DESC
        LIMIT 50
        """)
    List<Task> findByCustomerId(UUID customerId);

    // ── Обновление статуса ────────────────────────────────────────────

    @Modifying
    @Query("""
        UPDATE tasks
        SET status_id = :statusId, updated_at = NOW(),
            completed_at = CASE
                WHEN (SELECT code FROM task_statuses WHERE id = :statusId) = 'DONE'
                THEN NOW() ELSE completed_at END
        WHERE id = :id
        """)
    void updateStatus(UUID id, UUID statusId);

    // ── Переназначение ────────────────────────────────────────────────

    @Modifying
    @Query("UPDATE tasks SET assignee_id = :assigneeId, updated_at = NOW() WHERE id = :id")
    void updateAssignee(UUID id, UUID assigneeId);
}
