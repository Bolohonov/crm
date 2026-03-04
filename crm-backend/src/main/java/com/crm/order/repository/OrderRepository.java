package com.crm.order.repository;

import com.crm.order.entity.Order;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends CrudRepository<Order, UUID> {

    @Query("""
        SELECT o.*
        FROM orders o
        WHERE (:customerId IS NULL OR o.customer_id = :customerId::uuid)
          AND (:statusId   IS NULL OR o.status_id   = :statusId::uuid)
          AND (:authorId   IS NULL OR o.author_id   = :authorId::uuid)
        ORDER BY o.created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    List<Order> findAll(String customerId, String statusId, String authorId,
                        int limit, int offset);

    @Query("""
        SELECT COUNT(*) FROM orders o
        WHERE (:customerId IS NULL OR o.customer_id = :customerId::uuid)
          AND (:statusId   IS NULL OR o.status_id   = :statusId::uuid)
          AND (:authorId   IS NULL OR o.author_id   = :authorId::uuid)
        """)
    long countAll(String customerId, String statusId, String authorId);

    @Query("SELECT * FROM orders WHERE customer_id = :customerId ORDER BY created_at DESC LIMIT 20")
    List<Order> findByCustomerId(UUID customerId);

    @Modifying
    @Query("UPDATE orders SET status_id = :statusId, updated_at = NOW() WHERE id = :id")
    void updateStatus(UUID id, UUID statusId);

    @Modifying
    @Query("UPDATE orders SET total_amount = :amount, updated_at = NOW() WHERE id = :id")
    void updateTotal(UUID id, BigDecimal amount);

    /** Статистика: сумма заказов по статусам для дашборда */
    @Query("""
        SELECT os.code AS status_code, COUNT(o.id) AS cnt, COALESCE(SUM(o.total_amount), 0) AS total
        FROM orders o
        JOIN order_statuses os ON os.id = o.status_id
        GROUP BY os.code
        """)
    List<java.util.Map<String, Object>> getStatsByStatus();
}
