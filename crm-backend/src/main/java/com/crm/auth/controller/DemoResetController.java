package com.crm.auth.controller;

import com.crm.auth.service.DemoResetService;
import com.crm.common.config.AppProperties;
import com.crm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Внутренний endpoint для сброса демо-схемы.
 *
 * POST /internal/demo/reset
 *   Header: X-Internal-Token: <значение из app.internal-token>
 *
 * Защита — статический токен из конфига (не JWT).
 * Endpoint намеренно НЕ добавлен в PUBLIC_ENDPOINTS — он проходит
 * через JwtAuthFilter, но мы проверяем токен вручную до любой логики.
 * Таким образом даже без валидного JWT запрос будет отклонён
 * если токен неверный.
 *
 * Вызывается k8s CronJob через curl раз в ночь.
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class DemoResetController {

    private final DemoResetService demoResetService;
    private final AppProperties    appProperties;

    @PostMapping("/demo/reset")
    public ResponseEntity<ApiResponse<String>> reset(
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {

        String expected = appProperties.getInternalToken();

        if (expected == null || expected.isBlank() || !expected.equals(token)) {
            log.warn("Demo reset rejected: invalid or missing X-Internal-Token");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Invalid token"));
        }

        log.info("Demo reset triggered via internal endpoint");
        demoResetService.reset();

        return ResponseEntity.ok(ApiResponse.ok("Demo schema reset completed"));
    }
}
