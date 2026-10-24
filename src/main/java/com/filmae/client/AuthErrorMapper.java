package com.filmae.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmae.dto.common.ApiFieldError;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Converte respostas de erro do auth-microservice em {@link ApiException} do contrato.
 *
 * <p>Formatos do auth: {@code ErrorResponse {timestamp, status, error, message, path}} e, para falha de
 * Bean Validation, {@code {status: 400, error: "VALIDATION_ERROR", message, errors: {campo: mensagem}}}.
 * A senha fraca volta como 400 sem {@code errors}, só com a mensagem.
 */
@Slf4j
public class AuthErrorMapper {

    static final String WEAK_PASSWORD_MESSAGE =
        "A senha precisa ter pelo menos 8 caracteres, com letra maiúscula, letra minúscula, número e caractere especial";
    static final String UNAVAILABLE_MESSAGE = "Serviço de autenticação indisponível no momento";

    private final ObjectMapper objectMapper;

    public AuthErrorMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * @param status              status HTTP devolvido pelo auth
     * @param body                corpo bruto da resposta (pode ser vazio ou não ser JSON)
     * @param unauthorizedMessage mensagem do 401 para a operação (ex.: credenciais inválidas no login)
     */
    public ApiException map(HttpStatusCode status, byte[] body, String unauthorizedMessage) {
        int code = status.value();
        if (code == HttpStatus.CONFLICT.value()) {
            return new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS, "Este e-mail já está cadastrado");
        }
        if (code == HttpStatus.BAD_REQUEST.value()) {
            return validationError(readJson(body));
        }
        if (code == HttpStatus.UNAUTHORIZED.value() || code == HttpStatus.FORBIDDEN.value()) {
            // O auth não tem entry point próprio: requisição sem autenticação válida sai como 403.
            return ApiException.unauthorized(unauthorizedMessage);
        }
        if (code == HttpStatus.TOO_MANY_REQUESTS.value()) {
            return new ApiException(ErrorCode.RATE_LIMIT_EXCEEDED);
        }
        log.warn("Resposta inesperada do auth-microservice: HTTP {}", code);
        return ApiException.dependencyUnavailable(UNAVAILABLE_MESSAGE, null);
    }

    private ApiException validationError(JsonNode json) {
        List<ApiFieldError> fields = new ArrayList<>();
        JsonNode errors = json.path("errors");
        if (errors.isObject()) {
            errors.fields().forEachRemaining(entry ->
                fields.add(ApiFieldError.of(entry.getKey(), entry.getValue().asText())));
            fields.sort(Comparator.comparing(ApiFieldError::getField));
        }

        String authMessage = json.path("message").asText("");
        if (fields.isEmpty() && authMessage.toLowerCase(Locale.ROOT).contains("password")) {
            fields.add(ApiFieldError.of("password", WEAK_PASSWORD_MESSAGE));
        }
        return new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.getDefaultMessage(),
            fields.isEmpty() ? null : fields);
    }

    private JsonNode readJson(byte[] body) {
        if (body == null || body.length == 0) {
            return objectMapper.missingNode();
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            return node != null ? node : objectMapper.missingNode();
        } catch (IOException ex) {
            return objectMapper.missingNode();
        }
    }
}
