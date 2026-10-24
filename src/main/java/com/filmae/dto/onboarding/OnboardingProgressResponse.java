package com.filmae.dto.onboarding;

import com.filmae.entity.OnboardingStep;

import java.util.List;

/**
 * {@code OnboardingProgress} do front. {@code currentStep} nunca é {@code complete} (o tipo do front exclui
 * esse valor): com todas as etapas salvas e o onboarding ainda não concluído, volta {@code profileStory}.
 * {@code genres} são nomes do IMDb; {@code profileStory} é {@code null} até a etapa ser salva.
 */
public record OnboardingProgressResponse(
    boolean completed,
    OnboardingStep currentStep,
    List<String> genres,
    List<OnboardingMovieDto> favoriteMovies,
    String profileStory
) {
}
