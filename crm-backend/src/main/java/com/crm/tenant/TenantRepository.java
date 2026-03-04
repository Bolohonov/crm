package com.crm.tenant;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepository extends CrudRepository<Tenant, UUID> {

    Optional<Tenant> findBySchemaName(String schemaName);

    @Modifying
    @Query("""
        UPDATE public.tenants
        SET status = :status, updated_at = NOW()
        WHERE id = :id
        """)
    void updateStatus(UUID id, String status);
}
