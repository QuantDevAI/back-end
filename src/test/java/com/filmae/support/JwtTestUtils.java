package com.filmae.support;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Gera tokens no mesmo formato do auth-microservice (HS512, issuer {@code auth-service},
 * claims {@code roles}, {@code email}, {@code name}). O access token tem {@code roles};
 * o refresh token não.
 */
public class JwtTestUtils {

    /** Igual a {@code jwt.secret} em application-test.yml. */
    public static final String TEST_SECRET =
        "filmae-test-shared-jwt-secret-only-for-tests-0123456789abcdefghijklmnopqrstuvwxyz";

    private static final String ISSUER = "auth-service";

    private final SecretKey key;

    public JwtTestUtils() {
        this(TEST_SECRET);
    }

    public JwtTestUtils(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String accessToken(UUID authUserId, String email, String name) {
        return accessToken(authUserId, email, name, Duration.ofMinutes(15));
    }

    public String accessToken(UUID authUserId, String email, String name, Duration ttl) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(authUserId.toString())
            .issuer(ISSUER)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(ttl)))
            .claim("roles", "ROLE_USER")
            .claim("email", email)
            .claim("name", name)
            .signWith(key, Jwts.SIG.HS512)
            .compact();
    }

    public String accessTokenWithIssuer(UUID authUserId, String issuer) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(authUserId.toString())
            .issuer(issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofMinutes(15))))
            .claim("roles", "ROLE_USER")
            .signWith(key, Jwts.SIG.HS512)
            .compact();
    }

    public String expiredAccessToken(UUID authUserId, String email, String name) {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        return Jwts.builder()
            .subject(authUserId.toString())
            .issuer(ISSUER)
            .issuedAt(Date.from(past.minus(Duration.ofMinutes(15))))
            .expiration(Date.from(past))
            .claim("roles", "ROLE_USER")
            .claim("email", email)
            .claim("name", name)
            .signWith(key, Jwts.SIG.HS512)
            .compact();
    }

    public String refreshToken(UUID authUserId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(authUserId.toString())
            .issuer(ISSUER)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(Duration.ofDays(30))))
            .signWith(key, Jwts.SIG.HS512)
            .compact();
    }

    /** Header {@code Authorization} pronto. */
    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
