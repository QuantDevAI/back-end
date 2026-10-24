package com.filmae.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.filmae.client.dto.AuthLoginRequest;
import com.filmae.client.dto.AuthRefreshRequest;
import com.filmae.client.dto.AuthRegisterRequest;
import com.filmae.client.dto.AuthTokenResponse;
import com.filmae.client.dto.AuthUserResponse;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("AuthServiceClient (MockRestServiceServer)")
class AuthServiceClientTest {

    private static final String BASE_URL = "http://auth.test";

    private MockRestServiceServer server;
    private AuthServiceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AuthServiceClient(builder.build(), new ObjectMapper());
    }

    @AfterEach
    void verify() {
        server.verify();
    }

    @Test
    @DisplayName("login envia e-mail e senha e lê o LoginResponse do auth")
    void loginParsesTokens() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/login"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.email").value("ana@example.com"))
            .andExpect(jsonPath("$.password").value("Senha@123"))
            .andRespond(withSuccess("""
                {"accessToken":"at","refreshToken":"rt","tokenType":"Bearer","expiresIn":900000}
                """, MediaType.APPLICATION_JSON));

        AuthTokenResponse tokens = client.login(new AuthLoginRequest("ana@example.com", "Senha@123"));

        assertThat(tokens.accessToken()).isEqualTo("at");
        assertThat(tokens.refreshToken()).isEqualTo("rt");
        assertThat(tokens.expiresIn()).isEqualTo(900000L);
    }

    @Test
    @DisplayName("register lê o UserResponse do auth ignorando campos extras")
    void registerParsesUser() {
        UUID id = UUID.randomUUID();
        server.expect(requestTo(BASE_URL + "/api/v1/auth/register"))
            .andExpect(jsonPath("$.name").value("Ana"))
            .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body("""
                {"id":"%s","name":"Ana","email":"ana@example.com","emailVerified":false,
                 "status":"PENDING_VERIFICATION","roles":["ROLE_USER"],"createdAt":"2026-10-24T10:00:00"}
                """.formatted(id)));

        AuthUserResponse user = client.register(new AuthRegisterRequest("Ana", "ana@example.com", "Senha@123"));

        assertThat(user.id()).isEqualTo(id);
        assertThat(user.email()).isEqualTo("ana@example.com");
    }

    @Test
    @DisplayName("refresh repassa o refresh token")
    void refreshSendsToken() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/refresh"))
            .andExpect(jsonPath("$.refreshToken").value("rt-old"))
            .andRespond(withSuccess("""
                {"accessToken":"at2","refreshToken":"rt2","tokenType":"Bearer","expiresIn":900000}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.refresh(new AuthRefreshRequest("rt-old")).refreshToken()).isEqualTo("rt2");
    }

    @Test
    @DisplayName("logout repassa o Bearer ao auth")
    void logoutForwardsBearer() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/logout"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer access-123"))
            .andRespond(withStatus(HttpStatus.OK));

        client.logout("access-123");
    }

    @Test
    @DisplayName("409 do register vira EMAIL_ALREADY_EXISTS")
    void registerConflict() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/register"))
            .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":409,\"error\":\"CONFLICT\",\"message\":\"Email already registered\"}"));

        assertThatThrownBy(() -> client.register(new AuthRegisterRequest("Ana", "ana@example.com", "Senha@123")))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS));
    }

    @Test
    @DisplayName("400 com errors vira VALIDATION_ERROR com fields")
    void registerValidation() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/register"))
            .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":400,\"error\":\"VALIDATION_ERROR\",\"errors\":{\"email\":\"Email must be valid\"}}"));

        assertThatThrownBy(() -> client.register(new AuthRegisterRequest("Ana", "x", "Senha@123")))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                assertThat(ex.getFields()).singleElement().satisfies(f -> assertThat(f.getField()).isEqualTo("email"));
            });
    }

    @Test
    @DisplayName("401 do login vira UNAUTHORIZED com mensagem de credenciais")
    void loginUnauthorized() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/login"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                .body("{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Invalid credentials\"}"));

        assertThatThrownBy(() -> client.login(new AuthLoginRequest("ana@example.com", "errada")))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED);
                assertThat(ex.getMessage()).isEqualTo("E-mail ou senha inválidos");
            });
    }

    @Test
    @DisplayName("401 do refresh vira UNAUTHORIZED")
    void refreshUnauthorized() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/refresh"))
            .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.refresh(new AuthRefreshRequest("rt")))
            .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("5xx vira DEPENDENCY_UNAVAILABLE")
    void serverErrorBecomesDependencyUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/login"))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).body("{\"message\":\"An unexpected error occurred\"}"));

        assertDependencyUnavailable(() -> client.login(new AuthLoginRequest("a@b.com", "x")));
    }

    @Test
    @DisplayName("timeout de leitura vira DEPENDENCY_UNAVAILABLE")
    void timeoutBecomesDependencyUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/login"))
            .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertDependencyUnavailable(() -> client.login(new AuthLoginRequest("a@b.com", "x")));
    }

    @Test
    @DisplayName("falha de conexão vira DEPENDENCY_UNAVAILABLE")
    void connectionErrorBecomesDependencyUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/refresh"))
            .andRespond(withException(new ConnectException("Connection refused")));

        assertDependencyUnavailable(() -> client.refresh(new AuthRefreshRequest("rt")));
    }

    @Test
    @DisplayName("resposta 200 sem tokens vira DEPENDENCY_UNAVAILABLE")
    void missingTokensBecomeDependencyUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/login"))
            .andRespond(withSuccess("{\"tokenType\":\"Bearer\"}", MediaType.APPLICATION_JSON));

        assertDependencyUnavailable(() -> client.login(new AuthLoginRequest("a@b.com", "x")));
    }

    @Test
    @DisplayName("resposta 200 com JSON inválido vira DEPENDENCY_UNAVAILABLE")
    void unreadableBodyBecomesDependencyUnavailable() {
        server.expect(requestTo(BASE_URL + "/api/v1/auth/login"))
            .andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));

        assertDependencyUnavailable(() -> client.login(new AuthLoginRequest("a@b.com", "x")));
    }

    @Test
    @DisplayName("porta fechada (conexão real recusada) vira DEPENDENCY_UNAVAILABLE")
    void realConnectionRefused() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500));
        factory.setReadTimeout(Duration.ofMillis(500));
        AuthServiceClient realClient = new AuthServiceClient(
            RestClient.builder().baseUrl("http://127.0.0.1:" + port).requestFactory(factory).build(), new ObjectMapper());

        assertDependencyUnavailable(() -> realClient.login(new AuthLoginRequest("a@b.com", "x")));
    }

    private static void assertDependencyUnavailable(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, ex -> {
            assertThat(ex.getCode()).isEqualTo(ErrorCode.DEPENDENCY_UNAVAILABLE);
            assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        });
    }
}
