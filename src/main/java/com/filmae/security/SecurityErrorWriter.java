package com.filmae.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmae.dto.common.ApiErrorResponse;
import com.filmae.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Escreve o envelope de erro direto na resposta (usado fora do DispatcherServlet). */
@Component
@RequiredArgsConstructor
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(code.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code, message));
    }
}
