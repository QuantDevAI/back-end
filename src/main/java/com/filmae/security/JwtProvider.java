package com.filmae.security;

import com.filmae.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Valida (não emite) os access tokens do auth-microservice: HS512 com o segredo
 * compartilhado. Só aceita tokens com o claim {@code roles}, que é o que diferencia
 * o access token do refresh token no auth.
 */
@Slf4j
@Component
public class JwtProvider {

    public static final String ROLES_CLAIM = "roles";
    public static final int MIN_SECRET_BYTES = 64;

    private final SecretKey signingKey;
    private final String issuer;

    public JwtProvider(JwtProperties properties) {
        String secret = properties.secret();
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("jwt.secret (JWT_SECRET) não configurado");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("jwt.secret precisa ter pelo menos " + MIN_SECRET_BYTES + " bytes para HS512");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
        this.issuer = properties.issuer();
    }

    /** Valida o token e devolve o usuário autenticado, ou vazio se não for um access token válido. */
    public Optional<AuthenticatedUser> parseAccessToken(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);

            if (!Jwts.SIG.HS512.getId().equals(jws.getHeader().getAlgorithm())) {
                log.debug("JWT rejeitado: algoritmo {}", jws.getHeader().getAlgorithm());
                return Optional.empty();
            }

            Claims claims = jws.getPayload();
            if (StringUtils.hasText(issuer) && !issuer.equals(claims.getIssuer())) {
                log.debug("JWT rejeitado: issuer {}", claims.getIssuer());
                return Optional.empty();
            }

            Object rolesClaim = claims.get(ROLES_CLAIM);
            if (rolesClaim == null) {
                log.debug("JWT rejeitado: sem claim roles (refresh token?)");
                return Optional.empty();
            }

            UUID authUserId = UUID.fromString(claims.getSubject());
            return Optional.of(new AuthenticatedUser(
                authUserId,
                claims.get("email", String.class),
                claims.get("name", String.class),
                parseRoles(rolesClaim),
                token
            ));
        } catch (JwtException | IllegalArgumentException | NullPointerException ex) {
            log.debug("JWT inválido: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private static List<String> parseRoles(Object rolesClaim) {
        if (rolesClaim instanceof String roles) {
            return Arrays.stream(roles.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        }
        if (rolesClaim instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
