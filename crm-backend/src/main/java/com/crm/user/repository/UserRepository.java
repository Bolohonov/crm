package com.crm.user.repository;

import com.crm.user.entity.User;
import com.crm.user.entity.UserStatus;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends CrudRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<User> findBySsoProviderAndSsoId(String ssoProvider, String ssoId);

    @Query("SELECT * FROM public.users WHERE tenant_id = :tenantId")
    Iterable<User> findAllByTenantId(UUID tenantId);

    @Query("SELECT COUNT(*) FROM public.users WHERE tenant_id = :tenantId AND status != 'BLOCKED'")
    int countActiveByTenantId(UUID tenantId);

    @Modifying
    @Query("""
        UPDATE public.users
        SET status = :status, updated_at = :updatedAt
        WHERE id = :id
        """)
    void updateStatus(UUID id, String status, Instant updatedAt);

    @Modifying
    @Query("""
        UPDATE public.users
        SET email_verified = true, status = 'ACTIVE', updated_at = NOW()
        WHERE id = :userId
        """)
    void verifyEmail(UUID userId);

    @Modifying
    @Query("""
        UPDATE public.users
        SET tenant_id = :tenantId, updated_at = NOW()
        WHERE id = :userId
        """)
    void assignTenant(UUID userId, UUID tenantId);

    @Modifying
    @Query("""
        UPDATE public.users
        SET password_hash = :passwordHash, updated_at = NOW()
        WHERE id = :userId
        """)
    void updatePassword(UUID userId, String passwordHash);
}
