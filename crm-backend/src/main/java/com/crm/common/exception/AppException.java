package com.crm.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Базовое бизнес-исключение. Все доменные исключения наследуются от него.
 *
 * Пример использования:
 *   throw new AppException("USER_NOT_FOUND", "Пользователь не найден", HttpStatus.NOT_FOUND);
 *
 * Или через фабричные методы подклассов:
 *   throw UserNotFoundException.byEmail(email);
 */
@Getter
public class AppException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public AppException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    // ---- Фабричные методы для типовых ошибок ----

    public static AppException notFound(String entity) {
        return new AppException(
            entity.toUpperCase() + "_NOT_FOUND",
            entity + " не найден",
            HttpStatus.NOT_FOUND
        );
    }

    public static AppException conflict(String code, String message) {
        return new AppException(code, message, HttpStatus.CONFLICT);
    }

    public static AppException badRequest(String code, String message) {
        return new AppException(code, message, HttpStatus.BAD_REQUEST);
    }

    public static AppException unauthorized(String message) {
        return new AppException("UNAUTHORIZED", message, HttpStatus.UNAUTHORIZED);
    }

    public static AppException forbidden(String message) {
        return new AppException("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}
