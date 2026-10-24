package com.filmae.security;

import com.filmae.config.JwtProperties;
import com.filmae.support.JwtTestUtils;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.MacAlgorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtProvider")
class JwtProviderTest {

    private static final String OTHER_SECRET =
        "outro-segredo-qualquer-com-mais-de-64-bytes-0123456789abcdefghijklmnopqrstuvwxyz";

    private final JwtProvider provider = new JwtProvider(new JwtProperties(JwtTestUtils.TEST_SECRET, "auth-service"));
    private final JwtTestUtils jwt = new JwtTestUtils();

    @Test
    @DisplayName("Aceita access token do auth e extrai sub, email, name e roles")
    void parsesValidAccessToken() {
        UUID id = UUID.randomUUID();
        String token = jwt.accessToken(id, "ana@example.com", "Ana");

        assertThat(provider.parseAccessToken(token)).hasValueSatisfying(user -> {
            assertThat(user.authUserId()).isEqualTo(id);
            assertThat(user.email()).isEqualTo("ana@example.com");
            assertThat(user.name()).isEqualTo("Ana");
            assertThat(user.roles()).containsExactly("ROLE_USER");
            assertThat(user.token()).isEqualTo(token);
        });
    }

    @Test
    @DisplayName("Separa roles em CSV e aceita roles como lista")
    void parsesRolesFormats() {
        SignedToken csv = token(b -> b.claim("roles", "ROLE_USER, ROLE_ADMIN"));
        assertThat(provider.parseAccessToken(csv.value())).hasValueSatisfying(u ->
            assertThat(u.roles()).containsExactly("ROLE_USER", "ROLE_ADMIN"));

        SignedToken list = token(b -> b.claim("roles", List.of("ROLE_USER")));
        assertThat(provider.parseAccessToken(list.value())).hasValueSatisfying(u ->
            assertThat(u.roles()).containsExactly("ROLE_USER"));
    }

    @Test
    @DisplayName("Rejeita token expirado")
    void rejectsExpired() {
        assertThat(provider.parseAccessToken(jwt.expiredAccessToken(UUID.randomUUID(), "a@b.com", "A"))).isEmpty();
    }

    @Test
    @DisplayName("Rejeita assinatura com outro segredo")
    void rejectsWrongSignature() {
        String foreign = new JwtTestUtils(OTHER_SECRET).accessToken(UUID.randomUUID(), "a@b.com", "A");
        assertThat(provider.parseAccessToken(foreign)).isEmpty();
    }

    @Test
    @DisplayName("Rejeita issuer diferente de auth-service")
    void rejectsWrongIssuer() {
        assertThat(provider.parseAccessToken(jwt.accessTokenWithIssuer(UUID.randomUUID(), "outro-servico"))).isEmpty();
    }

    @Test
    @DisplayName("Sem issuer configurado, não verifica issuer")
    void skipsIssuerCheckWhenBlank() {
        JwtProvider lenient = new JwtProvider(new JwtProperties(JwtTestUtils.TEST_SECRET, ""));
        assertThat(lenient.parseAccessToken(jwt.accessTokenWithIssuer(UUID.randomUUID(), "outro-servico"))).isPresent();
    }

    @Test
    @DisplayName("Rejeita refresh token (sem claim roles)")
    void rejectsRefreshToken() {
        assertThat(provider.parseAccessToken(jwt.refreshToken(UUID.randomUUID()))).isEmpty();
    }

    @Test
    @DisplayName("Rejeita token HS256, mesmo assinado com o segredo certo")
    void rejectsNonHs512() {
        SignedToken hs256 = token(b -> b.claim("roles", "ROLE_USER"), Jwts.SIG.HS256);
        assertThat(provider.parseAccessToken(hs256.value())).isEmpty();
    }

    @Test
    @DisplayName("Rejeita sub que não é UUID")
    void rejectsNonUuidSubject() {
        SignedToken token = token(b -> b.subject("123").claim("roles", "ROLE_USER"));
        assertThat(provider.parseAccessToken(token.value())).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"nao.e.jwt", "abc", "   "})
    @DisplayName("Rejeita entrada vazia ou malformada")
    void rejectsGarbage(String input) {
        assertThat(provider.parseAccessToken(input)).isEmpty();
    }

    @Test
    @DisplayName("Exige segredo de pelo menos 64 bytes")
    void requiresStrongSecret() {
        assertThatThrownBy(() -> new JwtProvider(new JwtProperties("curto", "auth-service")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("64");
        assertThatThrownBy(() -> new JwtProvider(new JwtProperties("x".repeat(63), "auth-service")))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProvider(new JwtProperties("", "auth-service")))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProvider(new JwtProperties(null, "auth-service")))
            .isInstanceOf(IllegalStateException.class);
        new JwtProvider(new JwtProperties("x".repeat(64), "auth-service"));
    }

    private record SignedToken(String value) {
    }

    private SignedToken token(UnaryOperator<JwtBuilder> customizer) {
        return token(customizer, Jwts.SIG.HS512);
    }

    private SignedToken token(UnaryOperator<JwtBuilder> customizer,
                              MacAlgorithm alg) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
            .subject(UUID.randomUUID().toString())
            .issuer("auth-service")
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofMinutes(5))));
        builder = customizer.apply(builder);
        var key = Keys.hmacShaKeyFor(JwtTestUtils.TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return new SignedToken(builder.signWith(key, alg).compact());
    }
}
