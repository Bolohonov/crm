package com.crm.customer.dto;

import jakarta.validation.Valid;
import lombok.*;

@Getter
@Setter
public class CustomerUpdateRequest {
    private String status;

    @Valid
    private PersonalDataRequest personalData;

    @Valid
    private OrgDataRequest orgData;
}
