package com.filmae.dto.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshSessionRequest(
    @NotBlank(message = "Refresh token é obrigatório")
    String refreshToken
) {

    @Override
    public String toString() {
        return "RefreshSessionRequest[***]";
    }
}
