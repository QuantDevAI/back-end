package com.filmae.dto.dashboard;

/** Gênero IMDb (ex.: {@code Sci-Fi}) e quantidade de filmes assistidos com ele. */
public record DashboardGenreInsight(String genre, long count) {
}
