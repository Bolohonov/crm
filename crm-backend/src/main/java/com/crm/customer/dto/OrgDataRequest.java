package com.crm.customer.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.util.UUID;

@Getter
@Setter
public class OrgDataRequest {
    @NotBlank(message = "Название организации обязательно")
    @Size(max = 512)
    private String orgName;

    private UUID legalFormId;

    @NotBlank(message = "ИНН обязателен")
    @Pattern(regexp = "^(\\d{10}|\\d{12})$", message = "ИНН: 10 или 12 цифр")
    private String inn;

    @Pattern(regexp = "^\\d{9}$", message = "КПП: 9 цифр")
    private String kpp;

    @NotBlank(message = "ОГРН обязателен")
    @Pattern(regexp = "^(\\d{13}|\\d{15})$", message = "ОГРН: 13 или 15 цифр")
    private String ogrn;

    @Size(max = 512)
    private String address;
}
