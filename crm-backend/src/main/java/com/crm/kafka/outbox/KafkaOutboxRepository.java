package com.crm.kafka.outbox;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface KafkaOutboxRepository extends CrudRepository<KafkaOutbox, UUID> {

    /** Выбираем PENDING записи в порядке создания (batch для поллера). */
    @Query("""
        SELECT * FROM public.kafka_outbox
        WHERE status = 'PENDING'
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """)
    List<KafkaOutbox> findPendingForUpdate(int limit);

    @Modifying
    @Query("""
        UPDATE public.kafka_outbox
        SET status = 'PUBLISHED', published_at = :publishedAt, attempt_count = attempt_count + 1
        WHERE id = :id
        """)
    void markPublished(UUID id, Instant publishedAt);

    @Modifying
    @Query("""
        UPDATE public.kafka_outbox
        SET attempt_count = attempt_count + 1,
            last_error    = :error,
            status        = CASE WHEN attempt_count + 1 >= 5 THEN 'FAILED' ELSE 'PENDING' END
        WHERE id = :id
        """)
    void markAttemptFailed(UUID id, String error);

    /** Для мониторинга: количество записей по статусам. */
    @Query("SELECT COUNT(*) FROM public.kafka_outbox WHERE status = :status")
    long countByStatus(String status);
}
