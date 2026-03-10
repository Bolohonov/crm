package com.crm.auth.dto;

import lombok.Setter;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Ответ на успешный логин / обновление токена.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private long expiresIn;       // секунды до истечения access-токена

    private UUID userId;
    private String email;
    private String fullName;
    private String userType;
    private String tenantSchema;
}
