package com.crm.tenant.dto;

import lombok.Setter;
import lombok.Getter;
import lombok.Builder;

@Getter
@Setter
@Builder
public class ModuleResponse {
    private String  code;
    private String  name;
    private String  description;
    private boolean enabled;
    private boolean required;
}
