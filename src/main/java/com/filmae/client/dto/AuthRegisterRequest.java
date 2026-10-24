package com.filmae.client.dto;

public record AuthRegisterRequest(String name, String email, String password) {

    @Override
    public String toString() {
        return "AuthRegisterRequest[name=" + name + ", email=" + email + "]";
    }
}
