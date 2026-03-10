package com.crm.customer.dto;

import com.crm.customer.entity.CustomerType;
import lombok.*;

@Getter
@Setter
public class CustomerSearchRequest {
    private String query;        // полнотекстовый поиск
    private CustomerType type;
    private String status;
    private int page = 0;
    private int size = 20;
}
