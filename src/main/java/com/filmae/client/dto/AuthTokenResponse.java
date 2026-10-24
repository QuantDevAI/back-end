package com.filmae.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code LoginResponse} do auth-microservice (login e refresh). {@code expiresIn} vem em milissegundos. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthTokenResponse(String accessToken, String refreshToken, String tokenType, Long expiresIn) {

    @Override
    public String toString() {
        return "AuthTokenResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + "]";
    }
}
