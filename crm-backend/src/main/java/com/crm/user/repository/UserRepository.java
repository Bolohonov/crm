package com.crm.user.repository;

import com.crm.user.entity.User;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends CrudRepository<User, UUID> {

    @Query("SELECT * FROM public.users_global WHERE tenant_id = :tenantId")
    Iterable<User> findAllByTenantId(UUID tenantId);

    @Query("SELECT COUNT(*) FROM public.users_global WHERE tenant_id = :tenantId AND status != 'BLOCKED'")
    int countActiveByTenantId(UUID tenantId);

    @Query("SELECT EXISTS(SELECT 1 FROM public.users_global WHERE email = :email AND tenant_id = :tenantId)")
    boolean existsByEmailAndTenantId(String email, UUID tenantId);

    @Modifying
    @Query("UPDATE public.users_global SET status = :status, updated_at = :updatedAt WHERE id = :id")
    void updateStatus(UUID id, String status, Instant updatedAt);

    @Modifying
    @Query("UPDATE public.users_global SET email_verified = true, status = 'ACTIVE', updated_at = NOW() WHERE id = :userId")
    void verifyEmail(UUID userId);

    @Modifying
    @Query("UPDATE public.users_global SET tenant_id = :tenantId, updated_at = NOW() WHERE id = :userId")
    void assignTenant(UUID userId, UUID tenantId);

    @Modifying
    @Query("UPDATE public.users_global SET password_hash = :passwordHash, updated_at = NOW() WHERE id = :userId")
    void updatePassword(UUID userId, String passwordHash);

    @Query("SELECT * FROM public.users_global WHERE email = :email LIMIT 1")
    Optional<User> findByEmail(String email);

    @Query("SELECT EXISTS(SELECT 1 FROM public.users_global WHERE email = :email)")
    boolean existsByEmail(String email);

    @Query("SELECT * FROM public.users_global WHERE sso_provider = :ssoProvider AND sso_id = :ssoId LIMIT 1")
    Optional<User> findBySsoProviderAndSsoId(String ssoProvider, String ssoId);
}
