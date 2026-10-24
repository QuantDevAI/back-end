package com.filmae.dto.onboarding;

import java.time.Instant;

/** {@code CompleteOnboardingResponse} do front: {@code {completed: true, completedAt, nextScreen: "home"}}. */
public record CompleteOnboardingResponse(boolean completed, Instant completedAt, String nextScreen) {

    public static CompleteOnboardingResponse of(Instant completedAt) {
        return new CompleteOnboardingResponse(true, completedAt, "home");
    }
}
