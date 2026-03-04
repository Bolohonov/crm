package com.crm.customer.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
@Table("customers")
public class Customer {

    @Id private UUID id;
    private CustomerType customerType;
    private String status;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer c)) return false;
        return Objects.equals(id, c.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
