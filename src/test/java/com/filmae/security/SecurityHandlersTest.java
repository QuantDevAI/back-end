package com.filmae.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RestAuthenticationEntryPoint e RestAccessDeniedHandler")
class SecurityHandlersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecurityErrorWriter writer = new SecurityErrorWriter(objectMapper);
    private final RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(writer);
    private final RestAccessDeniedHandler accessDeniedHandler = new RestAccessDeniedHandler(writer);

    @BeforeEach
    void setRequestId() {
        MDC.put("requestId", "req_SEC");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("401 com envelope UNAUTHORIZED, JSON UTF-8 e WWW-Authenticate")
    void entryPointWritesEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(new MockHttpServletRequest(), response, new InsufficientAuthenticationException("sem token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
        assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer");
        JsonNode error = objectMapper.readTree(response.getContentAsByteArray()).get("error");
        assertThat(error.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(error.get("message").asText()).isNotBlank().doesNotContain("sem token");
        assertThat(error.get("requestId").asText()).isEqualTo("req_SEC");
        assertThat(error.has("fields")).isFalse();
    }

    @Test
    @DisplayName("403 com envelope FORBIDDEN e mensagem padrão")
    void accessDeniedWritesEnvelope() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        accessDeniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("detalhe interno"));

        assertThat(response.getStatus()).isEqualTo(403);
        JsonNode error = objectMapper.readTree(response.getContentAsByteArray()).get("error");
        assertThat(error.get("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(error.get("message").asText()).isEqualTo("Acesso não permitido");
        assertThat(error.get("requestId").asText()).isEqualTo("req_SEC");
    }

    @Test
    @DisplayName("Não escreve nada se a resposta já foi enviada")
    void skipsCommittedResponse() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        response.setCommitted(true);

        accessDeniedHandler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("x"));

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
