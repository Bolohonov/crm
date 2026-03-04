package com.crm.auth.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("public.email_verifications")
public class EmailVerification {

    @Id
    private UUID id;

    private UUID userId;
    private String token;
    private EmailVerificationType type;
    private Instant expiresAt;
    private boolean used;
    private Instant createdAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !used && !isExpired();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EmailVerification ev)) return false;
        return Objects.equals(id, ev.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
