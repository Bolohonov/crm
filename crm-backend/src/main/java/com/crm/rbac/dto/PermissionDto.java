package com.crm.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

public class PermissionDto {

    @Getter
    @Setter
    @EqualsAndHashCode
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionResponse {
        private UUID   id;
        private String code;
        private String name;
        private String description;
        private String module;
    }
}
