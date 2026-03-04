package com.crm.order.repository;

import com.crm.order.entity.OrderItem;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends CrudRepository<OrderItem, UUID> {

    @Query("SELECT * FROM order_items WHERE order_id = :orderId ORDER BY id")
    List<OrderItem> findByOrderId(UUID orderId);

    @Modifying
    @Query("DELETE FROM order_items WHERE order_id = :orderId")
    void deleteByOrderId(UUID orderId);

    @Query("SELECT COALESCE(SUM(total_price), 0) FROM order_items WHERE order_id = :orderId")
    BigDecimal sumByOrderId(UUID orderId);

    @Modifying
    @Query("""
        UPDATE order_items
        SET quantity = :quantity, total_price = :quantity * price
        WHERE id = :id AND order_id = :orderId
        """)
    void updateQuantity(UUID id, UUID orderId, BigDecimal quantity);
}
