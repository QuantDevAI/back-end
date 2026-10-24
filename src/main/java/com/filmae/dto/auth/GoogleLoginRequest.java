package com.filmae.dto.auth;

public record GoogleLoginRequest(String idToken) {

    @Override
    public String toString() {
        return "GoogleLoginRequest[***]";
    }
}
