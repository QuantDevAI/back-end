package com.filmae.dto.profile;

import com.filmae.entity.UserPreferences;

/** {@code UserProfilePreferences} do front. */
public record UserPreferencesResponse(boolean receiveRecommendationsByEmail, boolean spoilerModeEnabled) {

    public static UserPreferencesResponse from(UserPreferences preferences) {
        return new UserPreferencesResponse(preferences.isReceiveRecommendationsByEmail(), preferences.isSpoilerModeEnabled());
    }
}
