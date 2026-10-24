package com.filmae.exception;

import com.filmae.dto.common.ApiErrorResponse;
import com.filmae.dto.common.ApiFieldError;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        if (ex.getStatus().is5xxServerError()) {
            log.warn("ApiException {}: {}", ex.getCode(), ex.getMessage(), ex.getCause());
        }
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getFields());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiErrorResponse> handleBindException(BindException ex) {
        List<ApiFieldError> fields = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> ApiFieldError.of(error.getField(), error.getDefaultMessage()))
            .toList();
        return build(ErrorCode.VALIDATION_ERROR, null, fields);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        List<ApiFieldError> fields = ex.getParameterValidationResults().stream()
            .flatMap(result -> result.getResolvableErrors().stream()
                .map(error -> ApiFieldError.of(result.getMethodParameter().getParameterName(), error.getDefaultMessage())))
            .toList();
        return build(ErrorCode.VALIDATION_ERROR, null, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ApiFieldError> fields = ex.getConstraintViolations().stream()
            .map(violation -> ApiFieldError.of(lastNode(violation.getPropertyPath().toString()), violation.getMessage()))
            .toList();
        return build(ErrorCode.VALIDATION_ERROR, null, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(ErrorCode.VALIDATION_ERROR, "Corpo da requisição inválido ou mal formatado", null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(ErrorCode.VALIDATION_ERROR, null, List.of(ApiFieldError.of(ex.getName(), "Valor inválido")));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        return build(ErrorCode.VALIDATION_ERROR, null,
            List.of(ApiFieldError.of(ex.getParameterName(), "Parâmetro obrigatório")));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return build(ErrorCode.VALIDATION_ERROR, null,
            List.of(ApiFieldError.of(ex.getHeaderName(), "Header obrigatório")));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.VALIDATION_ERROR, "Método HTTP não suportado nesta rota", null);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.VALIDATION_ERROR, "Content-Type não suportado", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(ErrorCode.RESOURCE_NOT_FOUND, "Rota não encontrada", null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(AuthenticationException ex) {
        return build(ErrorCode.UNAUTHORIZED, null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(ErrorCode.FORBIDDEN, null, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Violação de integridade: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.STATE_CONFLICT, "Operação conflita com o estado atual dos dados", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex) {
        log.error("Erro não tratado", ex);
        return build(ErrorCode.INTERNAL_ERROR, null, null);
    }

    private ResponseEntity<ApiErrorResponse> build(ErrorCode code, String message, List<ApiFieldError> fields) {
        return build(code.getStatus(), code, message, fields);
    }

    private ResponseEntity<ApiErrorResponse> build(HttpStatus status, ErrorCode code, String message, List<ApiFieldError> fields) {
        return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, fields));
    }

    private static String lastNode(String path) {
        int idx = path.lastIndexOf('.');
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}
