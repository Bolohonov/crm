package com.crm.customer.dto;

import lombok.*;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class CustomerPageResponse {
    private java.util.List<CustomerResponse> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
}
