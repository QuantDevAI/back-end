package com.filmae.repository.projection;

/** Gênero IMDb e quantidade de filmes (projeção de queries nativas com {@code unnest(genres)}). */
public interface GenreCountView {

    String getGenre();

    Long getMovieCount();
}
