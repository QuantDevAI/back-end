package com.filmae.client.dto;

public record AuthLoginRequest(String email, String password) {

    @Override
    public String toString() {
        return "AuthLoginRequest[email=" + email + "]";
    }
}
