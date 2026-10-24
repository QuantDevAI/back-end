package com.filmae.dto.dashboard;

/** {@code DashboardKpis} do front ({@code src/types/dashboard.ts}). */
public record DashboardKpis(
    long watchedCount,
    long totalMinutesWatched,
    long favoriteCount,
    long rewatchCount,
    long ratedCount,
    long watchlistCount
) {
}
