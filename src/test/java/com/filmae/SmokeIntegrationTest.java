package com.filmae;

import com.filmae.support.AbstractIntegrationTest;
import com.filmae.support.JwtTestUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Smoke: health, envelope e autenticação")
class SmokeIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("GET /api/v1/health é público e usa o envelope data/meta")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", startsWith("req_")))
            .andExpect(jsonPath("$.data.status", equalTo("UP")))
            .andExpect(jsonPath("$.meta.requestId", startsWith("req_")));
    }

    @Test
    @DisplayName("X-Request-Id recebido é reaproveitado no header e no meta")
    void requestIdIsPropagated() throws Exception {
        mockMvc.perform(get("/api/v1/health").header("X-Request-Id", "req_TESTE123"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Request-Id", "req_TESTE123"))
            .andExpect(jsonPath("$.meta.requestId", equalTo("req_TESTE123")));
    }

    @Test
    @DisplayName("Actuator health é público")
    void actuatorHealthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", equalTo("UP")));
    }

    @Test
    @DisplayName("Rota protegida sem token responde 401 com envelope de erro")
    void protectedRouteWithoutTokenReturns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header("X-Request-Id", "req_SEMTOKEN"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")))
            .andExpect(jsonPath("$.error.message", notNullValue()))
            .andExpect(jsonPath("$.error.requestId", equalTo("req_SEMTOKEN")));
    }

    @Test
    @DisplayName("Refresh token não serve como access token (sem claim roles)")
    void refreshTokenIsRejected() throws Exception {
        String refresh = jwt.refreshToken(UUID.randomUUID());
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(refresh)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", equalTo("UNAUTHORIZED")));
    }

    @Test
    @DisplayName("Token expirado ou assinado com outro segredo responde 401")
    void expiredOrForeignTokenIsRejected() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/users/me")
                .header("Authorization", bearer(jwt.expiredAccessToken(id, "a@b.com", "A"))))
            .andExpect(status().isUnauthorized());

        String foreign = new JwtTestUtils("outro-segredo-qualquer-com-mais-de-64-bytes-0123456789abcdefghijklmnopqrstuvwxyz")
            .accessToken(id, "a@b.com", "A");
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", bearer(foreign)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Access token válido passa pela segurança (rota inexistente vira 404 no envelope)")
    void validTokenIsAccepted() throws Exception {
        mockMvc.perform(get("/api/v1/rota-que-nao-existe").header("Authorization", bearer(newUserToken())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code", equalTo("RESOURCE_NOT_FOUND")))
            .andExpect(jsonPath("$.error.requestId", startsWith("req_")));
    }
}
