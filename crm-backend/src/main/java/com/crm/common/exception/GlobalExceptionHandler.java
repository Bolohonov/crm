package com.crm.common.exception;

import com.crm.common.response.ApiError;
import com.crm.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Централизованная обработка исключений.
 * Все ошибки приводятся к единому формату ApiResponse.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Ошибки валидации (@Valid / @Validated) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(
            MethodArgumentNotValidException ex) {

        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Неверное значение",
                // Если несколько ошибок на одном поле — берём первую
                (first, second) -> first
            ));

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error(new ApiError("VALIDATION_ERROR", "Ошибка валидации", fields)));
    }

    /** Бизнес-исключения приложения */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        log.warn("Business exception: {} - {}", ex.getCode(), ex.getMessage());
        return ResponseEntity
            .status(ex.getHttpStatus())
            .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /** Неверный логин/пароль */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("INVALID_CREDENTIALS", "Неверный email или пароль"));
    }

    /** Недостаточно прав */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("ACCESS_DENIED", "Недостаточно прав для выполнения операции"));
    }

    /** Все остальные неожиданные ошибки */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", "Внутренняя ошибка сервера"));
    }
}
