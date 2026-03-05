package com.crm.auth.filter;

import com.crm.auth.service.JwtService;
import com.crm.tenant.TenantContext;
import com.crm.tenant.TenantRepository;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService        jwtService;
    private final UserRepository    userRepository;
    private final TenantRepository  tenantRepository;   // ← добавлено

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
            TenantContext.clear();
            SecurityContextHolder.clearContext();
        }
    }

    private void processValidToken(String token, HttpServletRequest request) {
        Optional<UUID>   userIdOpt = jwtService.extractUserId(token);
        Optional<String> schemaOpt = jwtService.extractTenantSchema(token);

        if (userIdOpt.isEmpty()) {
            log.warn("JWT token has no subject");
            return;
        }

        UUID userId = userIdOpt.get();

        // Устанавливаем TenantContext — и schema, и полный Tenant объект
        schemaOpt.filter(StringUtils::hasText).ifPresent(schema -> {
            TenantContext.set(schema);
            // FIX: загружаем Tenant объект чтобы getTenant() не возвращал null
            tenantRepository.findBySchemaName(schema).ifPresent(TenantContext::setTenant);
        });

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
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }

        String path = request.getServletPath();
        if (path.contains("/events/subscribe")) {
            String queryToken = request.getParameter("token");
            if (StringUtils.hasText(queryToken)) {
                return queryToken;
            }
        }

        return null;
    }

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
