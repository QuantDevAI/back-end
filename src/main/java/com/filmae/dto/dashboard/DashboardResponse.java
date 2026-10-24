package com.filmae.dto.dashboard;

import java.time.Instant;
import java.util.List;

/** {@code DashboardPayload} do front. */
public record DashboardResponse(
    DashboardKpis kpis,
    List<DashboardGenreInsight> topGenres,
    List<WatchHistoryEntry> historyPreview,
    Instant updatedAt
) {
}
