package com.filmae.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.filmae.exception.ErrorCode;
import com.filmae.util.RequestContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Envelope de erro do contrato: {@code {error: {code, message, fields?, requestId}}}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiErrorResponse {

    private ApiError error;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public static class ApiError {
        private ErrorCode code;
        private String message;
        private List<ApiFieldError> fields;
        private String requestId;
    }

    public static ApiErrorResponse of(ErrorCode code, String message) {
        return of(code, message, null);
    }

    public static ApiErrorResponse of(ErrorCode code, String message, List<ApiFieldError> fields) {
        return new ApiErrorResponse(ApiError.builder()
            .code(code)
            .message(message != null ? message : code.getDefaultMessage())
            .fields(fields)
            .requestId(RequestContext.requestId())
            .build());
    }
}
