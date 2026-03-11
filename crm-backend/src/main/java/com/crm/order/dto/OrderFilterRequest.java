package com.crm.order.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class OrderFilterRequest {
    private UUID customerId;
    private UUID statusId;
    private UUID authorId;
    private String query;
    private LocalDate dateFrom;
    private LocalDate dateTo;
    private int page = 0;
    private int size = 20;
}
