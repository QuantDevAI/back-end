package com.filmae.client.dto;

public record AuthRefreshRequest(String refreshToken) {

    @Override
    public String toString() {
        return "AuthRefreshRequest[***]";
    }
}
