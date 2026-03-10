package com.crm.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import lombok.Getter;

/** Административная смена пароля */
@Getter
@Setter
public class ChangePasswordRequest {
    @NotBlank @Size(min = 8, max = 100)
    private String newPassword;
}
