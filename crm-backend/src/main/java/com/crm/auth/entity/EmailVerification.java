package com.crm.auth.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table(schema = "public", value = "email_verifications")
public class EmailVerification {
    @EqualsAndHashCode.Include
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
}
