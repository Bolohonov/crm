package com.crm.customer.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Table("customer_org_data")
public class CustomerOrgData {
    @EqualsAndHashCode.Include
    @Id private UUID customerId;
    private String orgName;
    private UUID legalFormId;
    private String inn;
    private String kpp;
    private String ogrn;
    private String address;
    private Instant updatedAt;
}
