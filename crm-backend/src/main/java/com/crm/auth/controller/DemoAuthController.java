package com.crm.auth.controller;

import com.crm.auth.dto.AuthResponse;
import com.crm.auth.service.DemoAuthService;
import com.crm.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint для входа в демо-режим без регистрации.
 *
 * POST /auth/demo
 *   → возвращает обычный AuthResponse (accessToken + refreshToken)
 *   → токен привязан к фиксированному demo-пользователю в схеме tenant_demo
 *   → одновременно могут использовать ЛЮБОЕ количество пользователей:
 *     все получают один и тот же userId, но каждый запрос независим (stateless JWT)
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class DemoAuthController {

    private final DemoAuthService demoAuthService;

    @PostMapping("/demo")
    public ResponseEntity<ApiResponse<AuthResponse>> demo() {
        return ResponseEntity.ok(ApiResponse.ok(demoAuthService.loginAsDemo()));
    }
}
