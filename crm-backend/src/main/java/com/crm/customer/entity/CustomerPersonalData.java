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
@Table("customer_personal_data")
public class CustomerPersonalData {
    @EqualsAndHashCode.Include
    @Id private UUID customerId;
    private String firstName;
    private String lastName;
    private String middleName;
    private String phone;
    private String address;
    private String position;
    private Instant updatedAt;
}
