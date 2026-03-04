package com.crm.product.repository;

import com.crm.product.entity.Product;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends CrudRepository<Product, UUID> {

    @Query("""
        SELECT * FROM products
        WHERE (:onlyActive = false OR is_active = true)
          AND (:categoryId IS NULL OR category_id = :categoryId::uuid)
          AND (:query IS NULL OR
               name ILIKE '%' || :query || '%' OR
               sku  ILIKE '%' || :query || '%')
        ORDER BY name ASC
        LIMIT :limit OFFSET :offset
        """)
    List<Product> search(boolean onlyActive, String categoryId, String query, int limit, int offset);

    @Query("""
        SELECT COUNT(*) FROM products
        WHERE (:onlyActive = false OR is_active = true)
          AND (:categoryId IS NULL OR category_id = :categoryId::uuid)
          AND (:query IS NULL OR
               name ILIKE '%' || :query || '%' OR
               sku  ILIKE '%' || :query || '%')
        """)
    long countSearch(boolean onlyActive, String categoryId, String query);

    Optional<Product> findBySku(String sku);

    @Modifying
    @Query("UPDATE products SET is_active = :active, updated_at = NOW() WHERE id = :id")
    void setActive(UUID id, boolean active);
}
