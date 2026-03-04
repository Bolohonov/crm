package com.crm.status.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

public class StatusDto {

    @Data @Builder
    public static class StatusResponse {
        private UUID    id;
        private String  code;
        private String  name;
        private String  color;
        private int     sortOrder;
        private boolean isFinal;
        private boolean isSystem;
    }

    @Data
    public static class CreateRequest {
        @NotBlank @Size(max = 50)
        @Pattern(regexp = "^[A-Z_]+$", message = "Код должен содержать только заглавные буквы и _")
        private String code;

        @NotBlank @Size(max = 100)
        private String name;

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Цвет должен быть в формате #RRGGBB")
        private String color;

        private int     sortOrder;
        private boolean isFinal;
    }

    @Data
    public static class UpdateRequest {
        @NotBlank @Size(max = 100)
        private String name;

        @Pattern(regexp = "^#[0-9a-fA-F]{6}$")
        private String color;

        private int     sortOrder;
        private boolean isFinal;
    }
}
