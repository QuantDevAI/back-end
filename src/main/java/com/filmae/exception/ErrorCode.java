package com.filmae.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Códigos de erro aceitos pelo front ({@code ApiErrorCode} em {@code src/types/api.ts}).
 * {@code NETWORK_ERROR} e {@code UNKNOWN_ERROR} são gerados só no cliente.
 */
@Getter
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Os dados enviados são inválidos"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Autenticação necessária ou token inválido"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Acesso não permitido"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Recurso não encontrado"),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "E-mail já cadastrado"),
    STATE_CONFLICT(HttpStatus.CONFLICT, "Conflito de estado"),
    ONBOARDING_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "Onboarding incompleto"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Muitas requisições, tente novamente em instantes"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado"),
    DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Serviço dependente indisponível");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
