package com.crm.customer.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("customers")
public class Customer {
    @Id private UUID id;
    @Column("type")
    private CustomerType customerType;
    private String status;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;
}
