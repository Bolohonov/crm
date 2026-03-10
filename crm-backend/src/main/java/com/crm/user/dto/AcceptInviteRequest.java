package com.crm.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import lombok.Getter;

/** Принятие приглашения — установка пароля */
@Getter
@Setter
public class AcceptInviteRequest {
    @NotBlank
    private String token;
    @NotBlank @Size(min = 8, max = 100)
    private String password;
}
