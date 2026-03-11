package com.crm.status.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @JsonProperty("isFinal")
    private boolean isFinal;
    @JsonProperty("isSystem")
    private boolean isSystem;
}
