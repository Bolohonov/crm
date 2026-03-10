package com.crm.tenant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Setter;
import lombok.Getter;

@Getter
@Setter
public class UpdateSettingsRequest {
    @NotBlank @Size(max = 200)
    private String companyName;

    @Email @Size(max = 200)
    private String contactEmail;

    @Size(max = 20)
    private String contactPhone;

    @Size(max = 200)
    private String website;

    @Size(max = 500)
    private String logoUrl;

    @Size(max = 50)
    private String timezone;

    @Size(max = 10)
    private String currency;
}
