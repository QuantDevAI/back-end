package com.filmae.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code RegisterRequest} do front. O nome segue o limite de {@code users.name} (150). */
public record RegisterRequest(
    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 2, max = 150, message = "Nome deve ter entre 2 e 150 caracteres")
    String name,

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "E-mail inválido")
    @Size(max = 254, message = "E-mail deve ter no máximo 254 caracteres")
    String email,

    @NotBlank(message = "Senha é obrigatória")
    @Size(min = 8, max = 255, message = "Senha deve ter entre 8 e 255 caracteres")
    String password
) {

    @Override
    public String toString() {
        return "RegisterRequest[name=" + name + ", email=" + email + "]";
    }
}
