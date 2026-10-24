package com.filmae.dto.auth;

import com.filmae.entity.User;

/** {@code AuthUser} do front / {@code User} (4.1) do contrato. */
public record AuthUserDto(Long id, String email, String name, boolean onboardingCompleted) {

    public static AuthUserDto from(User user) {
        return new AuthUserDto(user.getId(), user.getEmail(), user.getName(), user.isOnboardingCompleted());
    }
}
