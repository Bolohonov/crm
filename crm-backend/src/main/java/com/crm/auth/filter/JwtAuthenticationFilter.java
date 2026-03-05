package com.crm.auth.filter;

import com.crm.auth.service.JwtService;
import com.crm.tenant.TenantContext;
import com.crm.user.entity.User;
import com.crm.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JWT-фильтр — выполняется один раз на каждый HTTP запрос.
 *
 * Порядок действий:
 *  1. Извлекает Bearer токен из заголовка Authorization
 *  2. Валидирует JWT
 *  3. Устанавливает TenantContext (tenantSchema из claims токена)
 *  4. Загружает пользователя из БД и устанавливает Authentication в SecurityContext
 *  5. В finally — очищает TenantContext чтобы не утекло в следующий запрос
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String token = extractToken(request);

            if (token != null && jwtService.isTokenValid(token)) {
                processValidToken(token, request);
            }

            filterChain.doFilter(request, response);

        } finally {
            // КРИТИЧНО: чистим ThreadLocal после каждого запроса
            // Без этого контекст утечёт в следующий запрос из пула потоков
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void processValidToken(String token, HttpServletRequest request) {
        Optional<UUID> userIdOpt = jwtService.extractUserId(token);
        Optional<String> schemaOpt = jwtService.extractTenantSchema(token);

        if (userIdOpt.isEmpty()) {
            log.warn("JWT token has no subject");
            return;
        }

        UUID userId = userIdOpt.get();

        // Устанавливаем TenantContext ДО любых запросов к БД
        // чтобы TenantAwareDataSource знал в какую схему идти
        schemaOpt.filter(StringUtils::hasText)
            .ifPresent(TenantContext::set);

        // Загружаем пользователя из БД (из public схемы — там search_path включает public)
        userRepository.findById(userId).ifPresentOrElse(
            user -> setAuthentication(user, token, request),
            () -> log.warn("User not found for JWT subject: {}", userId)
        );
    }

    private void setAuthentication(User user, String token, HttpServletRequest request) {
        if (!user.isActive()) {
            log.debug("User {} is not active, skipping authentication", user.getEmail());
            return;
        }

        // Роли Spring Security — добавим ROLE_ADMIN или ROLE_USER
        // Детальные permissions проверяются через @PreAuthorize на уровне методов
        List<SimpleGrantedAuthority> authorities = List.of(
            new SimpleGrantedAuthority("ROLE_" + user.getUserType().name())
        );

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(user, token, authorities);

        authentication.setDetails(
            new WebAuthenticationDetailsSource().buildDetails(request)
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated user: {}, schema: {}", user.getEmail(), TenantContext.get());
    }

    private String extractToken(HttpServletRequest request) {
        // 1. Стандартный способ — Bearer заголовок
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }

        // 2. Для SSE: EventSource не поддерживает кастомные заголовки,
        //    поэтому токен передаётся в query-параметре ?token=...
        //    Применяем ТОЛЬКО для /events/subscribe, чтобы не открывать дыру глобально
        String path = request.getServletPath();
        if (path.contains("/events/subscribe")) {
            String queryToken = request.getParameter("token");
            if (StringUtils.hasText(queryToken)) {
                return queryToken;
            }
        }

        return null;
    }

    /**
     * Пропускаем публичные эндпоинты без обработки токена.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/auth/login")
                || path.equals("/auth/register")
                || path.equals("/auth/refresh")
                || path.equals("/auth/verify")
                || path.startsWith("/actuator/");
    }
}
