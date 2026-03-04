package com.crm.customer.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("customer_personal_data")
public class CustomerPersonalData {
    @Id private UUID customerId;
    private String firstName;
    private String lastName;
    private String middleName;
    private String phone;
    private String address;
    private String position;
    private Instant updatedAt;
}
