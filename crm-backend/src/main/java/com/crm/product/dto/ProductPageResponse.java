package com.crm.product.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@Builder @NoArgsConstructor @AllArgsConstructor
public class ProductPageResponse {
    private List<ProductResponse> content;
    private long totalElements;
    private int totalPages;
    private int page;
    private int size;
}
