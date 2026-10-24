package com.filmae.service.movie;

import java.math.BigDecimal;
import java.util.List;

/** Filtros de GET /movies já com {@code genres} unificado ({@link GenreFilterParser}). */
public record MovieSearchCriteria(
    String q,
    List<String> genres,
    String type,
    Integer yearFrom,
    Integer yearTo,
    BigDecimal minRating,
    Integer page,
    Integer size
) {
}
