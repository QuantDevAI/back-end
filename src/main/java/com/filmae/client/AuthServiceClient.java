package com.filmae.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmae.client.dto.AuthLoginRequest;
import com.filmae.client.dto.AuthRefreshRequest;
import com.filmae.client.dto.AuthRegisterRequest;
import com.filmae.client.dto.AuthTokenResponse;
import com.filmae.client.dto.AuthUserResponse;
import com.filmae.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Cliente HTTP do auth-microservice ({@code /api/v1/auth/*}). Erros HTTP viram {@link ApiException}
 * pelo {@link AuthErrorMapper}; falha de conexão, timeout ou resposta ilegível viram
 * {@code DEPENDENCY_UNAVAILABLE}. O bean é criado em {@code AuthClientConfig}.
 */
@Slf4j
public class AuthServiceClient {

    static final String REGISTER_PATH = "/api/v1/auth/register";
    static final String LOGIN_PATH = "/api/v1/auth/login";
    static final String REFRESH_PATH = "/api/v1/auth/refresh";
    static final String LOGOUT_PATH = "/api/v1/auth/logout";

    private final RestClient restClient;
    private final AuthErrorMapper errorMapper;

    public AuthServiceClient(RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.errorMapper = new AuthErrorMapper(objectMapper);
    }

    public AuthUserResponse register(AuthRegisterRequest request) {
        return call(() -> requireBody(restClient.post()
            .uri(REGISTER_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                throw errorMapper.map(res.getStatusCode(), readBody(res), "Não autorizado");
            })
            .body(AuthUserResponse.class)));
    }

    public AuthTokenResponse login(AuthLoginRequest request) {
        return call(() -> requireTokens(restClient.post()
            .uri(LOGIN_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                throw errorMapper.map(res.getStatusCode(), readBody(res), "E-mail ou senha inválidos");
            })
            .body(AuthTokenResponse.class)));
    }

    public AuthTokenResponse refresh(AuthRefreshRequest request) {
        return call(() -> requireTokens(restClient.post()
            .uri(REFRESH_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                throw errorMapper.map(res.getStatusCode(), readBody(res), "Refresh token inválido ou expirado");
            })
            .body(AuthTokenResponse.class)));
    }

    /** Revoga os refresh tokens do usuário no auth, repassando o access token como Bearer. */
    public void logout(String accessToken) {
        call(() -> restClient.post()
            .uri(LOGOUT_PATH)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                throw errorMapper.map(res.getStatusCode(), readBody(res), "Token ausente, inválido ou expirado");
            })
            .toBodilessEntity());
    }

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (ApiException ex) {
            throw ex;
        } catch (ResourceAccessException ex) {
            log.warn("auth-microservice inacessível: {}", ex.getMessage());
            throw ApiException.dependencyUnavailable(AuthErrorMapper.UNAVAILABLE_MESSAGE, ex);
        } catch (RestClientException ex) {
            log.warn("Falha ao ler resposta do auth-microservice: {}", ex.getMessage());
            throw ApiException.dependencyUnavailable(AuthErrorMapper.UNAVAILABLE_MESSAGE, ex);
        }
    }

    private static <T> T requireBody(T body) {
        if (body == null) {
            throw ApiException.dependencyUnavailable("Resposta vazia do serviço de autenticação", null);
        }
        return body;
    }

    private static AuthTokenResponse requireTokens(AuthTokenResponse tokens) {
        requireBody(tokens);
        if (tokens.accessToken() == null || tokens.refreshToken() == null) {
            throw ApiException.dependencyUnavailable("Resposta sem tokens do serviço de autenticação", null);
        }
        return tokens;
    }

    private static byte[] readBody(ClientHttpResponse response) {
        try {
            return response.getBody().readAllBytes();
        } catch (IOException ex) {
            return new byte[0];
        }
    }
}
