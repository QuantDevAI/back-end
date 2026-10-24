package com.filmae.dto.profile;

import com.filmae.entity.User;

import java.time.Instant;

/** {@code UserProfile} do front. {@code bio} nunca é {@code null}. */
public record UserProfileResponse(Long id, String name, String email, String bio, Instant createdAt) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getBio(), user.getCreatedAt());
    }
}
