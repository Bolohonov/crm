package com.crm.sse;

import com.crm.tenant.TenantContext;
import com.crm.user.entity.User;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
        String tenantSchema = TenantContext.get();
        log.info("SSE subscribe: user={} tenant={}", user.getEmail(), tenantSchema);
        return notificationService.subscribe(tenantSchema);
    }
}