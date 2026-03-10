package com.crm.status.dto;

import lombok.Setter;
import lombok.Getter;
import lombok.Builder;
import java.util.UUID;

@Getter
@Setter
@Builder
public class StatusResponse {
    private UUID    id;
    private String  code;
    private String  name;
    private String  color;
    private int     sortOrder;
    private boolean isFinal;
    private boolean isSystem;
}
