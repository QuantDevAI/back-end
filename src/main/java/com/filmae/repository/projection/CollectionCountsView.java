package com.filmae.repository.projection;

/** Contagens da coleção de um usuário. {@code total} = filmes com pelo menos uma flag ativa. */
public interface CollectionCountsView {

    Long getTotal();

    Long getWatchlist();

    Long getWatched();

    Long getFavorite();

    Long getRewatch();
}
