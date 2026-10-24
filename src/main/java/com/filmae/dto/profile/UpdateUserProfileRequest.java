package com.filmae.dto.profile;

import jakarta.validation.constraints.Size;

/** PATCH {@code /users/me}: campos ausentes ficam como estão. O e-mail não é alterável aqui. */
public record UpdateUserProfileRequest(
    @Size(max = UpdateUserProfileRequest.NAME_MAX_LENGTH, message = "O nome deve ter no máximo 150 caracteres")
    String name,
    @Size(max = UpdateUserProfileRequest.BIO_MAX_LENGTH, message = "A bio deve ter no máximo 500 caracteres")
    String bio
) {

    public static final int NAME_MAX_LENGTH = 150;
    public static final int BIO_MAX_LENGTH = 500;
}
