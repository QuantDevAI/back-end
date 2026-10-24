package com.filmae.security;

import java.util.List;
import java.util.UUID;

/**
 * Principal colocado no SecurityContext pelo {@link JwtAuthenticationFilter}.
 *
 * @param authUserId {@code sub} do JWT (id do usuário no auth-microservice)
 * @param email      claim {@code email}
 * @param name       claim {@code name}
 * @param roles      claim {@code roles} já separado (ex.: {@code ROLE_USER})
 * @param token      access token bruto (útil para repassar ao auth, ex.: logout)
 */
public record AuthenticatedUser(UUID authUserId, String email, String name, List<String> roles, String token) {

    @Override
    public String toString() {
        return "AuthenticatedUser[authUserId=" + authUserId + ", email=" + email + ", roles=" + roles + "]";
    }
}
