package com.crm.rbac.dto;

import lombok.Setter;
import lombok.Getter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionResponse {
    private UUID   id;
    private String code;
    private String name;
    private String description;
    private String module;
}
