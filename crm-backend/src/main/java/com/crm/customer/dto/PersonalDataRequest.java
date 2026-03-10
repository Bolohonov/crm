package com.crm.customer.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
public class PersonalDataRequest {
    @NotBlank(message = "Имя обязательно")
    @Size(max = 128)
    private String firstName;

    @NotBlank(message = "Фамилия обязательна")
    @Size(max = 128)
    private String lastName;

    @Size(max = 128)
    private String middleName;

    @NotBlank(message = "Телефон обязателен")
    @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Формат: +7XXXXXXXXXX")
    private String phone;

    @Size(max = 512)
    private String address;

    @Size(max = 256)
    private String position;
}
