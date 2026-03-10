package com.crm.order.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class OrderPageResponse {
    private List<OrderResponse> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
}
