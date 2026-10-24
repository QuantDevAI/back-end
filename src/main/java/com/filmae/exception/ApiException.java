package com.filmae.exception;

import com.filmae.dto.common.ApiFieldError;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Exceção de negócio convertida no envelope de erro pelo {@link GlobalExceptionHandler}.
 * O status HTTP vem do {@link ErrorCode}, a menos que seja informado explicitamente.
 */
@Getter
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final List<ApiFieldError> fields;

    public ApiException(ErrorCode code) {
        this(code, code.getDefaultMessage(), code.getStatus(), null, null);
    }

    public ApiException(ErrorCode code, String message) {
        this(code, message, code.getStatus(), null, null);
    }

    public ApiException(ErrorCode code, String message, List<ApiFieldError> fields) {
        this(code, message, code.getStatus(), fields, null);
    }

    public ApiException(ErrorCode code, String message, Throwable cause) {
        this(code, message, code.getStatus(), null, cause);
    }

    public ApiException(ErrorCode code, String message, HttpStatus status, List<ApiFieldError> fields, Throwable cause) {
        super(message != null ? message : code.getDefaultMessage(), cause);
        this.code = code;
        this.status = status != null ? status : code.getStatus();
        this.fields = fields;
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    public static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, message);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
            List.of(ApiFieldError.of(field, message)));
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(ErrorCode.UNAUTHORIZED, message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(ErrorCode.FORBIDDEN, message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(ErrorCode.STATE_CONFLICT, message);
    }

    public static ApiException onboardingIncomplete(String message) {
        return new ApiException(ErrorCode.ONBOARDING_INCOMPLETE, message);
    }

    public static ApiException dependencyUnavailable(String message, Throwable cause) {
        return new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, message, cause);
    }
}
