package com.filmae.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmae.client.AuthServiceClient;
import com.filmae.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Fachada {@code /api/v1/auth/*} ponta a ponta, com o auth-microservice simulado por {@link MockRestServiceServer}. */
@Import(AuthControllerIntegrationTest.MockAuthServiceConfig.class)
@DisplayName("AuthController (integração)")
class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String AUTH_URL = "http://auth.mock";

    @TestConfiguration
    static class MockAuthServiceConfig {

        private final RestClient.Builder builder = RestClient.builder().baseUrl(AUTH_URL);
        private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        @Bean
        MockRestServiceServer authMockServer() {
            return server;
        }

        @Bean
        @Primary
        AuthServiceClient mockedAuthServiceClient(ObjectMapper objectMapper) {
            return new AuthServiceClient(builder.build(), objectMapper);
        }
    }

    @Autowired
    private MockRestServiceServer authServer;

    private final UUID authId = UUID.randomUUID();
    private final String email = "ana-" + authId + "@example.com";

    @BeforeEach
    void setUp() {
        cleanUserData();
        authServer.reset();
    }

    @AfterEach
    void verifyAuthCalls() {
        authServer.verify();
    }

    private String tokensJson(String accessToken) {
        return """
            {"accessToken":"%s","refreshToken":"rt-%s","tokenType":"Bearer","expiresIn":900000}
            """.formatted(accessToken, authId);
    }

    private void expectAuthLogin() {
        authServer.expect(requestTo(AUTH_URL + "/api/v1/auth/login"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(tokensJson(jwt.accessToken(authId, email, "Ana")), MediaType.APPLICATION_JSON));
    }

    private String loginBody() {
        return "{\"email\":\"" + email + "\",\"password\":\"Senha@123\"}";
    }

    @Test
    @DisplayName("register: 201 com AuthSession (sem nextScreen), cria usuário local e expiresIn em segundos")
    void registerCreatesSession() throws Exception {
        authServer.expect(requestTo(AUTH_URL + "/api/v1/auth/register"))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                {"id":"%s","name":"Ana","email":"%s","emailVerified":false,"status":"PENDING_VERIFICATION"}
                """.formatted(authId, email)));
        expectAuthLogin();

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"Senha@123\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data", aMapWithSize(4)))
            .andExpect(jsonPath("$.data.user", aMapWithSize(4)))
            .andExpect(jsonPath("$.data.user.id").isNumber())
            .andExpect(jsonPath("$.data.user.email", equalTo(email)))
            .andExpect(jsonPath("$.data.user.name", equalTo("Ana")))
            .andExpect(jsonPath("$.data.user.onboardingCompleted", equalTo(false)))
            .andExpect(jsonPath("$.data.accessToken").isString())
            .andExpect(jsonPath("$.data.refreshToken", equalTo("rt-" + authId)))
            .andExpect(jsonPath("$.data.expiresIn", equalTo(900)))
            .andExpect(jsonPath("$.data.nextScreen").doesNotExist())
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")));

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM users WHERE auth_user_id = ?", Integer.class, authId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("register: 409 do auth vira EMAIL_ALREADY_EXISTS no envelope de erro")
    void registerConflict() throws Exception {
        authServer.expect(requestTo(AUTH_URL + "/api/v1/auth/register"))
            .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":409,\"error\":\"CONFLICT\",\"message\":\"Email already registered\"}"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"Senha@123\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", equalTo("EMAIL_ALREADY_EXISTS")))
            .andExpect(jsonPath("$.error.message").isString())
            .andExpect(jsonPath("$.error.requestId", startsWith("req_")))
            .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("register: 400 do auth com errors vira VALIDATION_ERROR com fields")
    void registerAuthValidation() throws Exception {
        authServer.expect(requestTo(AUTH_URL + "/api/v1/auth/register"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":400,\"error\":\"VALIDATION_ERROR\",\"errors\":{\"email\":\"Email must be valid\"}}"));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ana\",\"email\":\"" + email + "\",\"password\":\"Senha@123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("email")))
            .andExpect(jsonPath("$.error.fields[0].message", equalTo("Email must be valid")));
    }

    @Test
    @DisplayName("register: validação local responde 400 sem chamar o auth")
    void registerLocalValidation() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"email\":\"nao-e-email\",\"password\":\"123\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")))
            .andExpect(jsonPath("$.error.fields[?(@.field == 'name')]").exists())
            .andExpect(jsonPath("$.error.fields[?(@.field == 'email')]").exists())
            .andExpect(jsonPath("$.error.fields[?(@.field == 'password')]").exists());
    }

    @Test
    @DisplayName("login: nextScreen onboarding e depois home quando o onboarding foi concluído")
    void loginNextScreen() throws Exception {
        expectAuthLogin();
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", aMapWithSize(5)))
            .andExpect(jsonPath("$.data.nextScreen", equalTo("onboarding")))
            .andExpect(jsonPath("$.data.expiresIn", equalTo(900)))
            .andExpect(jsonPath("$.data.user.onboardingCompleted", equalTo(false)))
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")));

        jdbcTemplate.update("UPDATE users SET onboarding_completed_at = now() WHERE auth_user_id = ?", authId);
        authServer.reset();
        expectAuthLogin();

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nextScreen", equalTo("home")))
            .andExpect(jsonPath("$.data.user.onboardingCompleted", equalTo(true)));
    }

    @Test
    @DisplayName("login: 401 do auth vira UNAUTHORIZED")
    void loginUnauthorized() throws Exception {
        authServer.expect(requestTo(AUTH_URL + "/api/v1/auth/login"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Invalid credentials\"}"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")))
            .andExpect(jsonPath("$.error.message", equalTo("E-mail ou senha inválidos")));
    }

    @Test
    @DisplayName("login: 5xx do auth vira 503 DEPENDENCY_UNAVAILABLE")
    void loginAuthDown() throws Exception {
        authServer.expect(requestTo(AUTH_URL + "/api/v1/auth/login"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code", equalTo("DEPENDENCY_UNAVAILABLE")));
    }

    @Test
    @DisplayName("login: timeout do auth vira 503 DEPENDENCY_UNAVAILABLE")
    void loginAuthTimeout() throws Exception {
        authServer.expect(requestTo(AUTH_URL + "/api/v1/auth/login"))
            .andRespond(withException(new SocketTimeoutException("Read timed out")));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code", equalTo("DEPENDENCY_UNAVAILABLE")));
    }

    @Test
    @DisplayName("refresh: devolve {accessToken, refreshToken, expiresIn}")
    void refresh() throws Exception {
        authServer.expect(requestTo(AUTH_URL + "/api/v1/auth/refresh"))
            .andRespond(withSuccess(tokensJson("novo-access"), MediaType.APPLICATION_JSON));

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"rt-antigo\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", aMapWithSize(3)))
            .andExpect(jsonPath("$.data.accessToken", equalTo("novo-access")))
            .andExpect(jsonPath("$.data.refreshToken", equalTo("rt-" + authId)))
            .andExpect(jsonPath("$.data.expiresIn", equalTo(900)))
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")));
    }

    @Test
    @DisplayName("refresh: 401 do auth vira UNAUTHORIZED; refreshToken vazio vira 400 sem chamar o auth")
    void refreshErrors() throws Exception {
        authServer.expect(requestTo(AUTH_URL + "/api/v1/auth/refresh"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"invalido\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));

        mockMvc.perform(post("/api/v1/auth/refresh").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.fields[0].field", equalTo("refreshToken")));
    }

    @Test
    @DisplayName("logout: 204 sem corpo e repassa o Bearer ao auth")
    void logout() throws Exception {
        String token = jwt.accessToken(authId, email, "Ana");
        authServer.expect(requestTo(AUTH_URL + "/api/v1/auth/logout"))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andRespond(withStatus(HttpStatus.OK));

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""));
    }

    @Test
    @DisplayName("logout: sem token responde 401 sem chamar o auth")
    void logoutWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("google: 503 DEPENDENCY_UNAVAILABLE")
    void google() throws Exception {
        mockMvc.perform(post("/api/v1/auth/google").contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"token-google\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error.code", equalTo("DEPENDENCY_UNAVAILABLE")))
            .andExpect(jsonPath("$.error.requestId", startsWith("req_")));
    }
}
