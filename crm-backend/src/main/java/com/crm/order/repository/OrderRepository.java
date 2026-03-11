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
    LEFT JOIN customers c ON c.id = o.customer_id
    LEFT JOIN customer_personal_data pd ON pd.customer_id = c.id
    LEFT JOIN customer_org_data od ON od.customer_id = c.id
    WHERE (:customerId IS NULL OR o.customer_id = :customerId::uuid)
      AND (:statusId   IS NULL OR o.status_id   = :statusId::uuid)
      AND (:authorId   IS NULL OR o.author_id   = :authorId::uuid)
      AND (:dateFrom   IS NULL OR o.created_at >= :dateFrom::date)
      AND (:dateTo     IS NULL OR o.created_at <  :dateTo::date + INTERVAL '1 day')
      AND (:query      IS NULL OR
           pd.last_name || ' ' || pd.first_name ILIKE '%' || :query || '%' OR
           od.org_name ILIKE '%' || :query || '%' OR
           CAST(o.external_order_id AS TEXT) ILIKE '%' || :query || '%')
    ORDER BY o.created_at DESC
    LIMIT :limit OFFSET :offset
    """)
    List<Order> findAll(String customerId, String statusId, String authorId,
                        String query, String dateFrom, String dateTo,
                        int limit, int offset);

    @Query("""
    SELECT COUNT(*) FROM orders o
    LEFT JOIN customers c ON c.id = o.customer_id
    LEFT JOIN customer_personal_data pd ON pd.customer_id = c.id
    LEFT JOIN customer_org_data od ON od.customer_id = c.id
    WHERE (:customerId IS NULL OR o.customer_id = :customerId::uuid)
      AND (:statusId   IS NULL OR o.status_id   = :statusId::uuid)
      AND (:authorId   IS NULL OR o.author_id   = :authorId::uuid)
      AND (:dateFrom   IS NULL OR o.created_at >= :dateFrom::date)
      AND (:dateTo     IS NULL OR o.created_at <  :dateTo::date + INTERVAL '1 day')
      AND (:query      IS NULL OR
           pd.last_name || ' ' || pd.first_name ILIKE '%' || :query || '%' OR
           od.org_name ILIKE '%' || :query || '%' OR
           CAST(o.external_order_id AS TEXT) ILIKE '%' || :query || '%')
    """)
    long countAll(String customerId, String statusId, String authorId,
                  String query, String dateFrom, String dateTo);

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
