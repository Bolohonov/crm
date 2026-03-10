package com.crm.customer.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class OrgDataResponse {
    private String orgName;
    private UUID legalFormId;
    private String legalFormName;
    private String inn;
    private String kpp;
    private String ogrn;
    private String address;
}
