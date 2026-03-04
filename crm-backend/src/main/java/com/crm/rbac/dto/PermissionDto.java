package com.crm.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

public class PermissionDto {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionResponse {
        private UUID id;
        private String code;
        private String name;
        private String description;
        private String module;
    }
}
