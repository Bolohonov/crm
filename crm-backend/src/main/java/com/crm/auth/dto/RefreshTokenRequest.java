
package com.crm.auth.dto;
import lombok.Setter;
import lombok.Getter;

import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token обязателен")
    private String refreshToken;
}
