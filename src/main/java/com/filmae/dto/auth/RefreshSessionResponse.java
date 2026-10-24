package com.filmae.dto.auth;

/** {@code RefreshSessionResponse} do front; {@code expiresIn} em segundos. */
public record RefreshSessionResponse(String accessToken, String refreshToken, long expiresIn) {

    @Override
    public String toString() {
        return "RefreshSessionResponse[expiresIn=" + expiresIn + "]";
    }
}
