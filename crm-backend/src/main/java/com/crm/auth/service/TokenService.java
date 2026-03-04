package com.crm.auth.service;

import com.crm.auth.entity.RefreshToken;
import com.crm.auth.repository.RefreshTokenRepository;
import com.crm.common.config.AppProperties;
import com.crm.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Управляет жизненным циклом refresh-токенов.
 *
 * Безопасность:
 * - Токен хранится в БД как SHA-256 хэш (не plaintext)
 * - Клиент получает plaintext UUID
 * - При logout/refresh — токен отзывается (revoked = true)
 * - Ротация токенов: при каждом refresh выдаётся новая пара
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AppProperties appProperties;

    /**
     * Сохраняет новый refresh-токен.
     *
     * @param userId    владелец токена
     * @param rawToken  plaintext токен (UUID строка)
     */
    @Transactional
    public void saveRefreshToken(UUID userId, String rawToken) {
        RefreshToken token = RefreshToken.builder()
            .userId(userId)
            .tokenHash(hash(rawToken))
            .expiresAt(Instant.now().plusSeconds(
                appProperties.getJwt().getRefreshTokenExpiration()
            ))
            .revoked(false)
            .createdAt(Instant.now())
            .build();

        refreshTokenRepository.save(token);
        log.debug("Refresh token saved for user: {}", userId);
    }

    /**
     * Проверяет refresh-токен и возвращает userId.
     * После валидации токен немедленно отзывается (refresh rotation).
     *
     * @param rawToken plaintext токен от клиента
     * @return UUID пользователя-владельца
     */
    @Transactional
    public UUID validateAndRotate(String rawToken) {
        String tokenHash = hash(rawToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> AppException.unauthorized("Refresh-токен не найден"));

        if (!token.isValid()) {
            // Если токен истёк или отозван — отзываем ВСЕ токены пользователя
            // (возможная попытка повторного использования — security incident)
            refreshTokenRepository.revokeAllByUserId(token.getUserId());
            log.warn("Expired/revoked refresh token used for user: {}", token.getUserId());
            throw AppException.unauthorized("Refresh-токен недействителен. Требуется повторный вход.");
        }

        // Ротация: старый токен сразу отзываем
        refreshTokenRepository.revokeByTokenHash(tokenHash);
        log.debug("Refresh token rotated for user: {}", token.getUserId());

        return token.getUserId();
    }

    /**
     * Отзывает все refresh-токены пользователя (logout).
     */
    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        log.debug("All refresh tokens revoked for user: {}", userId);
    }

    /**
     * Очистка устаревших токенов. Запускается каждую ночь в 03:00.
     */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteExpiredAndRevoked(Instant.now());
        log.info("Expired refresh tokens cleanup completed");
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
