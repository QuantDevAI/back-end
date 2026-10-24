package com.filmae.dto.onboarding;

import com.filmae.entity.OnboardingStep;

/** {@code nextStep} é sempre {@code complete}. */
public record SaveProfileStoryResponse(String profileStory, OnboardingStep nextStep) {
}
