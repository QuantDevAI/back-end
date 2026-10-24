package com.filmae.service.recommendation;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Gosto do usuário usado pela recomendação.
 *
 * @param userId              {@code users.id}
 * @param onboardingGenres    gêneros IMDb escolhidos no onboarding
 * @param historyGenreWeights peso (0 a 1, o maior vale 1) de cada gênero IMDb entre favoritos e notas altas
 */
public record TasteProfile(Long userId, Set<String> onboardingGenres, Map<String, Double> historyGenreWeights) {

    /** Peso mínimo para um gênero do histórico entrar em {@link #coreGenres()}. */
    public static final double CORE_HISTORY_WEIGHT = 0.5;

    public TasteProfile {
        onboardingGenres = onboardingGenres != null ? Set.copyOf(onboardingGenres) : Set.of();
        historyGenreWeights = historyGenreWeights != null ? Map.copyOf(historyGenreWeights) : Map.of();
    }

    public static TasteProfile empty(Long userId) {
        return new TasteProfile(userId, Set.of(), Map.of());
    }

    public boolean hasOnboarding() {
        return !onboardingGenres.isEmpty();
    }

    public boolean hasHistory() {
        return !historyGenreWeights.isEmpty();
    }

    public boolean isEmpty() {
        return !hasOnboarding() && !hasHistory();
    }

    public double historyWeight(String genre) {
        return historyGenreWeights.getOrDefault(genre, 0.0);
    }

    /** Gêneros que definem a "bolha": os do onboarding mais os fortes do histórico. */
    public Set<String> coreGenres() {
        Set<String> core = new LinkedHashSet<>(onboardingGenres);
        historyGenreWeights.forEach((genre, weight) -> {
            if (weight >= CORE_HISTORY_WEIGHT) {
                core.add(genre);
            }
        });
        return core;
    }
}
