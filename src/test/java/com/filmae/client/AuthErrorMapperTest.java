package com.filmae.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmae.dto.common.ApiFieldError;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("AuthErrorMapper")
class AuthErrorMapperTest {

    private final AuthErrorMapper mapper = new AuthErrorMapper(new ObjectMapper());

    private static byte[] json(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("409 do auth vira EMAIL_ALREADY_EXISTS")
    void conflictBecomesEmailAlreadyExists() {
        ApiException ex = mapper.map(HttpStatus.CONFLICT, json("""
            {"status":409,"error":"CONFLICT","message":"Email already registered","path":"/api/v1/auth/register"}
            """), "x");

        assertThat(ex.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("400 com mapa errors vira VALIDATION_ERROR com fields ordenados")
    void validationMapBecomesFields() {
        ApiException ex = mapper.map(HttpStatus.BAD_REQUEST, json("""
            {"status":400,"error":"VALIDATION_ERROR","message":"Validation failed",
             "errors":{"password":"Password is required","email":"Email must be valid"}}
            """), "x");

        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ex.getFields()).extracting(ApiFieldError::getField, ApiFieldError::getMessage)
            .containsExactly(
                tuple("email", "Email must be valid"),
                tuple("password", "Password is required"));
    }

    @Test
    @DisplayName("400 de senha fraca (sem errors) vira field password em português")
    void weakPasswordBecomesPasswordField() {
        ApiException ex = mapper.map(HttpStatus.BAD_REQUEST, json("""
            {"status":400,"error":"BAD_REQUEST","message":"Password must contain at least 8 characters, including ..."}
            """), "x");

        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(ex.getFields()).singleElement().satisfies(field -> {
            assertThat(field.getField()).isEqualTo("password");
            assertThat(field.getMessage()).isEqualTo(AuthErrorMapper.WEAK_PASSWORD_MESSAGE);
        });
    }

    @Test
    @DisplayName("400 sem corpo ou com corpo não JSON vira VALIDATION_ERROR sem fields")
    void badRequestWithoutBody() {
        assertThat(mapper.map(HttpStatus.BAD_REQUEST, new byte[0], "x").getFields()).isNull();
        ApiException ex = mapper.map(HttpStatus.BAD_REQUEST, json("<html>bad</html>"), "x");
        assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        assertThat(ex.getFields()).isNull();
    }

    @ParameterizedTest(name = "HTTP {0} vira UNAUTHORIZED")
    @ValueSource(ints = {401, 403})
    void unauthorizedAndForbiddenBecomeUnauthorized(int status) {
        ApiException ex = mapper.map(HttpStatusCode.valueOf(status), json("""
            {"status":401,"error":"UNAUTHORIZED","message":"Invalid credentials"}
            """), "E-mail ou senha inválidos");

        assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getMessage()).isEqualTo("E-mail ou senha inválidos");
    }

    @Test
    @DisplayName("429 vira RATE_LIMIT_EXCEEDED")
    void tooManyRequests() {
        assertThat(mapper.map(HttpStatus.TOO_MANY_REQUESTS, null, "x").getCode())
            .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @ParameterizedTest(name = "HTTP {0} vira DEPENDENCY_UNAVAILABLE")
    @ValueSource(ints = {404, 500, 502, 503, 504})
    void otherStatusesBecomeDependencyUnavailable(int status) {
        ApiException ex = mapper.map(HttpStatusCode.valueOf(status), json("{\"message\":\"boom\"}"), "x");

        assertThat(ex.getCode()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
        assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
