package com.crm.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import lombok.Getter;

/** Собственная смена пароля (требует старый пароль) */
@Getter
@Setter
public class SelfChangePasswordRequest {
    @NotBlank
    private String currentPassword;
    @NotBlank @Size(min = 8, max = 100)
    private String newPassword;
}
