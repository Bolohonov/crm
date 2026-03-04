package com.crm.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Map;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    /** Машиночитаемый код ошибки: USER_NOT_FOUND, INVALID_TOKEN, etc. */
    private final String code;

    /** Человекочитаемое сообщение */
    private final String message;

    /** Детали по полям (для ошибок валидации): { "email": "Неверный формат" } */
    private final Map<String, String> fields;
}
