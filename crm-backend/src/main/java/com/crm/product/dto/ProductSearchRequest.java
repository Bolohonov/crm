package com.crm.product.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
public class ProductSearchRequest {
    private String query;
    private UUID categoryId;
    private boolean onlyActive = true;
    private int page = 0;
    private int size = 20;
}
