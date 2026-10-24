package com.filmae.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/** {@code UserResponse} do auth-microservice (resposta do register). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthUserResponse(UUID id, String name, String email, String status) {
}
