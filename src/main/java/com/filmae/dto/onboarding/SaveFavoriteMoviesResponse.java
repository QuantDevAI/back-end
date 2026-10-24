package com.filmae.dto.onboarding;

import com.filmae.entity.OnboardingStep;

import java.util.List;

/** {@code nextStep} é sempre {@code profileStory}. */
public record SaveFavoriteMoviesResponse(List<OnboardingMovieDto> favoriteMovies, OnboardingStep nextStep) {
}
