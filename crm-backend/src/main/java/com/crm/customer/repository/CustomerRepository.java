package com.crm.customer.repository;

import com.crm.customer.entity.Customer;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomerRepository extends CrudRepository<Customer, UUID> {

    /**
     * Список клиентов с пагинацией и фильтрами.
     * search_path уже установлен TenantContext → запрос идёт в нужную схему.
     */
    @Query("""
        SELECT c.*
        FROM customers c
        WHERE (:type IS NULL OR c.type = :type)
          AND (:status IS NULL OR c.status = :status)
        ORDER BY c.created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    List<Customer> findAll(String type, String status, int limit, int offset);

    @Query("""
        SELECT COUNT(*)
        FROM customers c
        WHERE (:type IS NULL OR c.type = :type)
          AND (:status IS NULL OR c.status = :status)
        """)
    long countAll(String type, String status);

    /**
     * Полнотекстовый поиск по имени физлица.
     * Поддерживает prefix search: "техно" найдёт "ТехноПрогресс".
     * Каждое слово запроса получает суффикс :* через regexp_replace.
     */
    @Query("""
        SELECT c.*
        FROM customers c
        JOIN customer_personal_data pd ON pd.customer_id = c.id
        WHERE c.type IN ('INDIVIDUAL', 'SOLE_TRADER')
          AND (:type IS NULL OR c.type = :type)
          AND (:status IS NULL OR c.status = :status)
          AND pd.fts_name @@ to_tsquery('simple',
                regexp_replace(trim(lower(:query)), '\\s+', ':* & ', 'g') || ':*')
        ORDER BY ts_rank(pd.fts_name,
                to_tsquery('simple',
                regexp_replace(trim(lower(:query)), '\\s+', ':* & ', 'g') || ':*')) DESC
        LIMIT :limit OFFSET :offset
        """)
    List<Customer> searchPersonal(String query, String type, String status, int limit, int offset);

    /**
     * Полнотекстовый поиск по названию организации.
     * Поддерживает prefix search: "цифр" найдёт "АО Цифровые Решения".
     */
    @Query("""
        SELECT c.*
        FROM customers c
        JOIN customer_org_data od ON od.customer_id = c.id
        WHERE c.type IN ('LEGAL', 'SOLE_TRADER')
          AND (:type IS NULL OR c.type = :type)
          AND (:status IS NULL OR c.status = :status)
          AND od.fts_name @@ to_tsquery('simple',
                regexp_replace(trim(lower(:query)), '\\s+', ':* & ', 'g') || ':*')
        ORDER BY ts_rank(od.fts_name,
                to_tsquery('simple',
                regexp_replace(trim(lower(:query)), '\\s+', ':* & ', 'g') || ':*')) DESC
        LIMIT :limit OFFSET :offset
        """)
    List<Customer> searchOrg(String query, String type, String status, int limit, int offset);

    /**
     * Поиск по точному совпадению ИНН.
     */
    @Query("""
        SELECT c.*
        FROM customers c
        JOIN customer_org_data od ON od.customer_id = c.id
        WHERE od.inn = :inn
        """)
    List<Customer> findByInn(String inn);

    /**
     * Поиск по точному совпадению телефона.
     */
    @Query("""
        SELECT c.*
        FROM customers c
        JOIN customer_personal_data pd ON pd.customer_id = c.id
        WHERE pd.phone = :phone
        """)
    List<Customer> findByPhone(String phone);

    @Modifying
    @Query("""
        UPDATE customers SET status = :status, updated_at = NOW()
        WHERE id = :id
        """)
    void updateStatus(UUID id, String status);
}
