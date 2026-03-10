package com.crm.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Setter;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomerInfo {
    /** ID покупателя в системе магазина — для поиска существующего клиента в CRM. */
    private String externalId;
    private String firstName;
    private String lastName;
    private String middleName;
    private String email;
    private String phone;
    private String address;
}
