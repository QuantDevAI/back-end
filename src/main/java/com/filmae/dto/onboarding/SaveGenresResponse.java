package com.filmae.dto.onboarding;

import com.filmae.entity.OnboardingStep;

import java.util.List;

/** {@code genres} já normalizados para os nomes do IMDb; {@code nextStep} é sempre {@code favoriteMovies}. */
public record SaveGenresResponse(List<String> genres, OnboardingStep nextStep) {
}
