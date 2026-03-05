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
          AND (:status IS NULL OR c.is_active = :status)
        ORDER BY c.created_at DESC
        LIMIT :limit OFFSET :offset
        """)
    List<Customer> findAll(String type, String status, int limit, int offset);

    @Query("""
        SELECT COUNT(*)
        FROM customers c
        WHERE (:type IS NULL OR c.type = :type)
          AND (:status IS NULL OR c.is_active = :status)
        """)
    long countAll(String type, String status);

    /**
     * Полнотекстовый поиск по имени физлица через TSVECTOR-индекс.
     * plainto_tsquery преобразует строку в ts_query без спецсимволов.
     */
    @Query("""
        SELECT c.*
        FROM customers c
        JOIN customer_personal_data pd ON pd.customer_id = c.id
        WHERE c.type IN ('INDIVIDUAL', 'SOLE_TRADER')
          AND pd.fts_name @@ plainto_tsquery('russian', :query)
        ORDER BY ts_rank(pd.fts_name, plainto_tsquery('russian', :query)) DESC
        LIMIT :limit OFFSET :offset
        """)
    List<Customer> searchPersonal(String query, int limit, int offset);

    /**
     * Полнотекстовый поиск по названию организации.
     */
    @Query("""
        SELECT c.*
        FROM customers c
        JOIN customer_org_data od ON od.customer_id = c.id
        WHERE c.type IN ('LEGAL_ENTITY', 'SOLE_TRADER')
          AND od.fts_name @@ plainto_tsquery('russian', :query)
        ORDER BY ts_rank(od.fts_name, plainto_tsquery('russian', :query)) DESC
        LIMIT :limit OFFSET :offset
        """)
    List<Customer> searchOrg(String query, int limit, int offset);

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
