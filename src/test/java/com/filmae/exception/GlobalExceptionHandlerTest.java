package com.filmae.exception;

import com.filmae.config.RequestIdFilter;
import com.filmae.dto.common.ApiErrorResponse;
import com.filmae.dto.common.ApiFieldError;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @BeforeEach
    void setRequestId() {
        MDC.put("requestId", "req_HANDLER");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(ErrorCode.class)
    @DisplayName("Cada ErrorCode vira o status correspondente e o envelope de erro")
    void mapsEveryErrorCode(ErrorCode code) {
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(new ApiException(code));

        assertThat(response.getStatusCode()).isEqualTo(code.getStatus());
        ApiErrorResponse.ApiError error = response.getBody().getError();
        assertThat(error.getCode()).isEqualTo(code);
        assertThat(error.getMessage()).isEqualTo(code.getDefaultMessage());
        assertThat(error.getRequestId()).isEqualTo("req_HANDLER");
        assertThat(error.getFields()).isNull();
    }

    @Test
    @DisplayName("Status HTTP esperados pelo contrato")
    void contractStatuses() {
        assertThat(ErrorCode.VALIDATION_ERROR.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.UNAUTHORIZED.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.FORBIDDEN.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.RESOURCE_NOT_FOUND.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.EMAIL_ALREADY_EXISTS.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.STATE_CONFLICT.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.ONBOARDING_INCOMPLETE.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(ErrorCode.RATE_LIMIT_EXCEEDED.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.INTERNAL_ERROR.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(ErrorCode.DEPENDENCY_UNAVAILABLE.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("ApiException preserva mensagem e fields customizados")
    void keepsCustomMessageAndFields() {
        ResponseEntity<ApiErrorResponse> response = handler.handleApiException(
            ApiException.validation("rating", "Nota fora da escala"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getError().getFields())
            .containsExactly(ApiFieldError.of("rating", "Nota fora da escala"));

        ResponseEntity<ApiErrorResponse> notFound = handler.handleApiException(ApiException.notFound("Filme não encontrado"));
        assertThat(notFound.getBody().getError().getMessage()).isEqualTo("Filme não encontrado");
    }

    @Test
    @DisplayName("Exceção genérica vira INTERNAL_ERROR sem vazar a mensagem interna")
    void genericExceptionDoesNotLeak() {
        ResponseEntity<ApiErrorResponse> response = handler.handleGeneric(
            new IllegalStateException("senha do banco: s3cr3t"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getError().getCode()).isEqualTo(ErrorCode.INTERNAL_ERROR);
        assertThat(response.getBody().getError().getMessage())
            .isEqualTo(ErrorCode.INTERNAL_ERROR.getDefaultMessage())
            .doesNotContain("s3cr3t");
    }

    @Test
    @DisplayName("Segurança e integridade: 401, 403 e STATE_CONFLICT sem detalhes internos")
    void securityAndIntegrity() {
        assertThat(handler.handleAuthentication(new BadCredentialsException("x")).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(handler.handleAccessDenied(new AccessDeniedException("x")).getBody().getError().getCode())
            .isEqualTo(ErrorCode.FORBIDDEN);

        ResponseEntity<ApiErrorResponse> conflict = handler.handleDataIntegrity(
            new DataIntegrityViolationException("uk_users_auth_user_id"));
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().getError().getCode()).isEqualTo(ErrorCode.STATE_CONFLICT);
        assertThat(conflict.getBody().getError().getMessage()).doesNotContain("uk_users");
    }

    @Nested
    @DisplayName("Via MockMvc standalone (JSON real)")
    class ThroughMvc {

        private MockMvc mockMvc;

        @BeforeEach
        void setUp() {
            mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(handler)
                .addFilters(new RequestIdFilter())
                .build();
        }

        @Test
        @DisplayName("@Valid inválido → 400 VALIDATION_ERROR com fields")
        void validationWithFields() throws Exception {
            mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON)
                    .header("X-Request-Id", "req_VALID")
                    .content("{\"name\":\"\",\"email\":\"nao-e-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.error.message", equalTo(ErrorCode.VALIDATION_ERROR.getDefaultMessage())))
                .andExpect(jsonPath("$.error.requestId", equalTo("req_VALID")))
                .andExpect(jsonPath("$.error.fields", hasSize(2)))
                .andExpect(jsonPath("$.error.fields[*].field", containsInAnyOrder("name", "email")));
        }

        @Test
        @DisplayName("JSON malformado → 400 VALIDATION_ERROR")
        void malformedJson() throws Exception {
            mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")));
        }

        @Test
        @DisplayName("Tipo inválido e parâmetro faltando → 400 com o campo")
        void typeMismatchAndMissingParam() throws Exception {
            mockMvc.perform(get("/test/number/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field", equalTo("id")));

            mockMvc.perform(get("/test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields[0].field", equalTo("page")));
        }

        @Test
        @DisplayName("Método não suportado → 405 no envelope")
        void methodNotAllowed() throws Exception {
            mockMvc.perform(post("/test/param"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")));
        }

        @Test
        @DisplayName("ApiException → status do código, sem 'fields' no JSON quando vazio")
        void apiExceptionJson() throws Exception {
            mockMvc.perform(get("/test/incomplete"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", equalTo("ONBOARDING_INCOMPLETE")))
                .andExpect(jsonPath("$.error.fields").doesNotExist())
                .andExpect(jsonPath("$.error.requestId").exists());
        }

        @Test
        @DisplayName("Exceção inesperada → 500 INTERNAL_ERROR sem a mensagem original")
        void unexpected() throws Exception {
            mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code", equalTo("INTERNAL_ERROR")))
                .andExpect(content().string(not(containsString("detalhe-interno"))));
        }
    }

    @RestController
    static class TestController {

        @PostMapping("/test/validate")
        String validate(@Valid @RequestBody SampleRequest request) {
            return "ok";
        }

        @GetMapping("/test/number/{id}")
        String number(@PathVariable Long id) {
            return "ok";
        }

        @GetMapping("/test/param")
        String param(@RequestParam int page) {
            return "ok";
        }

        @GetMapping("/test/incomplete")
        String incomplete() {
            throw ApiException.onboardingIncomplete("Faltam etapas");
        }

        @GetMapping("/test/boom")
        String boom() {
            throw new RuntimeException("detalhe-interno");
        }
    }

    @Data
    static class SampleRequest {
        @NotBlank(message = "Nome obrigatório")
        private String name;

        @Email(message = "E-mail inválido")
        private String email;
    }
}
