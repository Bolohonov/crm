package com.crm.customer.dto;

import com.crm.customer.entity.CustomerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
public class CustomerCreateRequest {
    @NotNull(message = "Тип клиента обязателен")
    private CustomerType customerType;

    private String status = "NEW";

    /** Для INDIVIDUAL и SOLE_TRADER */
    @Valid
    private PersonalDataRequest personalData;

    /** Для LEGAL_ENTITY и SOLE_TRADER */
    @Valid
    private OrgDataRequest orgData;
}
