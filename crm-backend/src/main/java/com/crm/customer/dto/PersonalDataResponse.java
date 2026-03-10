package com.crm.customer.dto;

import lombok.*;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class PersonalDataResponse {
    private String firstName;
    private String lastName;
    private String middleName;
    private String fullName;
    private String phone;
    private String address;
    private String position;
}
