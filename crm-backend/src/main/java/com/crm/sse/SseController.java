package com.crm.sse;

import com.crm.auth.entity.User;
import com.crm.tenant.service.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE endpoint для push-уведомлений фронтенда.
 *
 * Клиент подключается один раз при открытии страницы:
 *   GET /api/v1/events/subscribe
 *   Authorization: Bearer <token>
 *
 * Соединение живёт до закрытия вкладки (или таймаута 30 мин).
 * EventSource в браузере автоматически переподключается при разрыве.
 */
@Slf4j
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Tag(name = "SSE Events", description = "Server-Sent Events для реалтайм уведомлений")
public class SseController {

    private final SseNotificationService notificationService;

    @GetMapping(value = "/subscribe", produces = "text/event-stream")
    @Operation(summary = "Подписаться на SSE-события тенанта")
    public SseEmitter subscribe(@AuthenticationPrincipal User user) {
        // TenantContext содержит текущую схему из JWT (устанавливается в JwtAuthenticationFilter)
        String tenantSchema = TenantContext.getSchema();

        log.info("SSE subscribe: user={} tenant={}", user.getEmail(), tenantSchema);

        return notificationService.subscribe(tenantSchema);
    }
}
