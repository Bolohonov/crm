package com.crm.auth.service;

import com.crm.common.config.AppProperties;
import com.crm.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис генерации и валидации JWT токенов.
 *
 * Структура access-токена:
 * {
 *   "sub": "user-uuid",
 *   "email": "user@example.com",
 *   "tenantSchema": "tenant_550e8400...",
 *   "userType": "ADMIN",
 *   "iat": 1234567890,
 *   "exp": 1234568790
 * }
 *
 * Access-токен живёт 15 минут — в payload кладём tenantSchema чтобы
 * JwtAuthFilter мог установить TenantContext без запроса к БД на каждый запрос.
 *
 * Refresh-токен — непрозрачный UUID, хранится в БД (хэш).
 * При обновлении access-токена проверяем refresh в БД и выдаём новую пару.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String CLAIM_EMAIL        = "email";
    private static final String CLAIM_TENANT_SCHEMA = "tenantSchema";
    private static final String CLAIM_USER_TYPE    = "userType";

    private final AppProperties appProperties;

    // ----------------------------------------------------------------
    //  Access Token
    // ----------------------------------------------------------------

    /**
     * Генерирует access-токен для пользователя.
     * tenantSchema может быть null для PENDING пользователей (до верификации email).
     */
    public String generateAccessToken(User user, String tenantSchema) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(appProperties.getJwt().getAccessTokenExpiration());

        return Jwts.builder()
            .subject(user.getId().toString())
            .claims(Map.of(
                CLAIM_EMAIL,         user.getEmail(),
                CLAIM_TENANT_SCHEMA, tenantSchema != null ? tenantSchema : "",
                CLAIM_USER_TYPE,     user.getUserType().name()
            ))
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(getSigningKey())
            .compact();
    }

    /**
     * Генерирует новый refresh-токен.
     * Это просто UUID — непрозрачный, хранится в БД как SHA-256 хэш.
     */
    public String generateRefreshToken() {
        return UUID.randomUUID().toString();
    }

    // ----------------------------------------------------------------
    //  Validation & Claims extraction
    // ----------------------------------------------------------------

    public boolean isTokenValid(String token) {
        return parseToken(token).isPresent();
    }

    public Optional<UUID> extractUserId(String token) {
        return parseToken(token)
            .map(claims -> UUID.fromString(claims.getSubject()));
    }

    public Optional<String> extractTenantSchema(String token) {
        return parseToken(token)
            .map(claims -> claims.get(CLAIM_TENANT_SCHEMA, String.class));
    }

    public Optional<String> extractEmail(String token) {
        return parseToken(token)
            .map(claims -> claims.get(CLAIM_EMAIL, String.class));
    }

    /**
     * Парсит токен и возвращает Claims.
     * Возвращает Optional.empty() если токен невалиден или истёк.
     */
    private Optional<Claims> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
            return Optional.of(claims);
        } catch (ExpiredJwtException e) {
            log.debug("JWT token expired");
            return Optional.empty();
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = appProperties.getJwt().getSecret()
            .getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
