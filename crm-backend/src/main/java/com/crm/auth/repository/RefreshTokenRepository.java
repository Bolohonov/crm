package com.crm.auth.repository;

import com.crm.auth.entity.RefreshToken;
import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends CrudRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE public.refresh_tokens SET is_revoked = true WHERE user_id = :userId")
    void revokeAllByUserId(UUID userId);

    @Modifying
    @Query("UPDATE public.refresh_tokens SET is_revoked = true WHERE token_hash = :tokenHash")
    void revokeByTokenHash(String tokenHash);

    /** Периодическая очистка устаревших токенов (вызывается по расписанию) */
    @Modifying
    @Query("DELETE FROM public.refresh_tokens WHERE expires_at < :now OR is_revoked = true")
    void deleteExpiredAndRevoked(Instant now);
}
