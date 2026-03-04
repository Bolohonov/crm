package com.crm.customer.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("customer_org_data")
public class CustomerOrgData {

    @Id private UUID customerId;
    private String orgName;
    private UUID legalFormId;
    private String inn;
    private String kpp;
    private String ogrn;
    private String address;
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomerOrgData c)) return false;
        return Objects.equals(customerId, c.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(customerId);
    }
}
