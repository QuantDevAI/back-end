package com.filmae.dto.onboarding;

import java.util.List;

/** tconsts dos filmes favoritos. Lista vazia é aceita (o front permite pular a etapa). */
public record SaveFavoriteMoviesRequest(List<String> movieIds) {
}
