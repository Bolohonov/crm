package com.crm.auth.repository;

import com.crm.auth.entity.EmailVerification;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationRepository extends CrudRepository<EmailVerification, UUID> {

    Optional<EmailVerification> findByToken(String token);

    @Modifying
    @Query("UPDATE public.email_verifications SET used = true WHERE id = :id")
    void markAsUsed(UUID id);

    @Modifying
    @Query("DELETE FROM public.email_verifications WHERE expires_at < :now OR used = true")
    void deleteExpiredAndUsed(Instant now);
}
