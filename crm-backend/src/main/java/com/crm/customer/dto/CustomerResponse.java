package com.crm.customer.dto;

import com.crm.customer.entity.CustomerType;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class CustomerResponse {
    private UUID id;
    private CustomerType customerType;
    private String status;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    /** Отображаемое имя — для списков */
    private String displayName;

    /** Краткая инфа — для карточек в списке */
    private String displayContact;  // телефон или ИНН

    private PersonalDataResponse personalData;
    private OrgDataResponse orgData;
}
