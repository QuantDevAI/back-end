package com.filmae.dto.profile;

/** PATCH {@code /users/me/preferences}: campos ausentes ficam como estão. */
public record UpdateUserPreferencesRequest(Boolean receiveRecommendationsByEmail, Boolean spoilerModeEnabled) {
}
