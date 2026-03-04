package com.crm.customer.dto;

import com.crm.customer.entity.CustomerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

public class CustomerDto {

    // ================================================================
    //  Запросы (Create / Update)
    // ================================================================

    @Data
    public static class CreateRequest {

        @NotNull(message = "Тип клиента обязателен")
        private CustomerType customerType;

        private String status = "NEW";

        /** Для INDIVIDUAL и SOLE_TRADER */
        @Valid
        private PersonalDataRequest personalData;

        /** Для LEGAL_ENTITY и SOLE_TRADER */
        @Valid
        private OrgDataRequest orgData;
    }

    @Data
    public static class UpdateRequest {
        private String status;

        @Valid
        private PersonalDataRequest personalData;

        @Valid
        private OrgDataRequest orgData;
    }

    @Data
    public static class PersonalDataRequest {
        @NotBlank(message = "Имя обязательно")
        @Size(max = 128)
        private String firstName;

        @NotBlank(message = "Фамилия обязательна")
        @Size(max = 128)
        private String lastName;

        @Size(max = 128)
        private String middleName;

        @NotBlank(message = "Телефон обязателен")
        @Pattern(regexp = "^\\+[1-9]\\d{6,14}$", message = "Формат: +7XXXXXXXXXX")
        private String phone;

        @Size(max = 512)
        private String address;

        @Size(max = 256)
        private String position;
    }

    @Data
    public static class OrgDataRequest {
        @NotBlank(message = "Название организации обязательно")
        @Size(max = 512)
        private String orgName;

        private UUID legalFormId;

        @NotBlank(message = "ИНН обязателен")
        @Pattern(regexp = "^(\\d{10}|\\d{12})$", message = "ИНН: 10 или 12 цифр")
        private String inn;

        @Pattern(regexp = "^\\d{9}$", message = "КПП: 9 цифр")
        private String kpp;

        @NotBlank(message = "ОГРН обязателен")
        @Pattern(regexp = "^(\\d{13}|\\d{15})$", message = "ОГРН: 13 или 15 цифр")
        private String ogrn;

        @Size(max = 512)
        private String address;
    }

    // ================================================================
    //  Поиск
    // ================================================================

    @Data
    public static class SearchRequest {
        private String query;        // полнотекстовый поиск
        private CustomerType type;
        private String status;
        private int page = 0;
        private int size = 20;
    }

    // ================================================================
    //  Ответы
    // ================================================================

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CustomerResponse {
        private UUID id;
        private CustomerType customerType;
        private String status;
        private UUID createdBy;
        private Instant createdAt;
        private Instant updatedAt;

        /** Отображаемое имя — для списков */
        private String displayName;

        /** Краткая инфа — для карточек в списке */
        private String displayContact;  // телефон или ИНН

        private PersonalDataResponse personalData;
        private OrgDataResponse orgData;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PersonalDataResponse {
        private String firstName;
        private String lastName;
        private String middleName;
        private String fullName;
        private String phone;
        private String address;
        private String position;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class OrgDataResponse {
        private String orgName;
        private UUID legalFormId;
        private String legalFormName;
        private String inn;
        private String kpp;
        private String ogrn;
        private String address;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PageResponse {
        private java.util.List<CustomerResponse> content;
        private long totalElements;
        private int totalPages;
        private int page;
        private int size;
    }
}
