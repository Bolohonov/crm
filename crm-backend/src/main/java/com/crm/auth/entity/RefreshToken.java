package com.crm.auth.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("public.refresh_tokens")
public class RefreshToken {

    @Id
    private UUID id;

    private UUID userId;

    /** SHA-256 хэш токена. Сам токен живёт только у клиента */
    private String tokenHash;

    private Instant expiresAt;
    private boolean revoked;
    private Instant createdAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !revoked && !isExpired();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefreshToken rt)) return false;
        return Objects.equals(id, rt.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
