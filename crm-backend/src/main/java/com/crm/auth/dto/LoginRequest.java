
package com.crm.auth.dto;
import lombok.Setter;
import lombok.Getter;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
public class LoginRequest {

    @NotBlank(message = "Email обязателен")
    @Email(message = "Неверный формат email")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    private String password;
}
