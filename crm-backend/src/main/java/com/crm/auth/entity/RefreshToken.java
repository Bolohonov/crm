package com.crm.auth.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(schema = "public", value = "refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    private UUID userId;

    /** SHA-256 хэш токена. Сам токен живёт только у клиента */
    private String tokenHash;

    private Instant expiresAt;

    @Column("is_revoked")
    private boolean revoked;

    private Instant createdAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }
}
