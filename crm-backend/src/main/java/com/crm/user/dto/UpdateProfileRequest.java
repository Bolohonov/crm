package com.crm.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import lombok.Getter;

/** Обновление профиля пользователя (не email, не пароль) */
@Getter
@Setter
public class UpdateProfileRequest {
    @NotBlank @Size(max = 100)
    private String firstName;
    @NotBlank @Size(max = 100)
    private String lastName;
    @Size(max = 100)
    private String middleName;
    @Size(max = 20)
    private String phone;
}
