package com.filmae.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;

/** Etapas do onboarding; {@link #getValue()} é o texto usado no banco e na API ({@code OnboardingStep} do front). */
public enum OnboardingStep {

    GENRES("genres"),
    FAVORITE_MOVIES("favoriteMovies"),
    PROFILE_STORY("profileStory"),
    COMPLETE("complete");

    private final String value;

    OnboardingStep(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static OnboardingStep fromValue(String value) {
        return Arrays.stream(values())
            .filter(step -> step.value.equals(value))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Etapa de onboarding desconhecida: " + value));
    }
}
