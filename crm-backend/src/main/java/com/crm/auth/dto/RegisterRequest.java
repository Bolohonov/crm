package com.crm.auth.dto;

import com.crm.tenant.TenantPlan;
import com.crm.user.entity.UserType;
import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * DTO для запроса регистрации нового пользователя.
 *
 * Два сценария:
 *  1. userType = ADMIN  → создаётся новый тенант, tenantSchema не нужна
 *  2. userType = REGULAR → adminEmail обязателен, пользователь привязывается к тенанту администратора
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Email обязателен")
    @Email(message = "Неверный формат email")
    private String email;

    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, message = "Пароль должен содержать минимум 8 символов")
    private String password;

    @NotBlank(message = "Имя обязательно")
    @Size(max = 128)
    private String firstName;

    @NotBlank(message = "Фамилия обязательна")
    @Size(max = 128)
    private String lastName;

    @Size(max = 128)
    private String middleName;

    @NotBlank(message = "Телефон обязателен")
    @Pattern(
        regexp = "^\\+[1-9]\\d{6,14}$",
        message = "Телефон должен быть в международном формате: +7XXXXXXXXXX"
    )
    private String phone;

    @NotNull(message = "Тип пользователя обязателен")
    private UserType userType;

    /** Для ADMIN: тариф. Для REGULAR: не используется */
    private TenantPlan plan = TenantPlan.FREE;

    /**
     * Для REGULAR пользователей — email их администратора.
     * По нему находим тенант и отправляем инвайт на подтверждение.
     */
    private String adminEmail;
}
