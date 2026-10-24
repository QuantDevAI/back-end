package com.filmae.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.Instant;

/** Item de {@code historyPreview}. {@code rating} é a nota do usuário e sai {@code null} quando não houver. */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record WatchHistoryEntry(
    String movieId,
    String title,
    Integer year,
    Instant watchedAt,
    BigDecimal rating
) {
}
