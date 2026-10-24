package com.filmae.service.recommendation;

import com.filmae.entity.Movie;
import com.filmae.entity.MovieRating;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Recomendação de filmes. A implementação atual é heurística ({@link HeuristicRecommendationService});
 * a interface existe para poder trocar pelo ai-microservice depois.
 *
 * <p>As operações recebem um {@link TasteProfile} já carregado para que uma mesma requisição
 * (ex.: GET /home ou uma página de busca) calcule o perfil uma vez só.
 */
public interface RecommendationService {

    TasteProfile profileFor(Long userId);

    /** Compatibilidade de 0 a 100. {@code imdbRating} pode ser {@code null}. */
    int score(TasteProfile profile, Movie movie, MovieRating imdbRating);

    /** Frases curtas explicando a compatibilidade (nunca vazio). */
    List<String> reasons(TasteProfile profile, Movie movie, MovieRating imdbRating);

    /** Filmes não assistidos mais compatíveis, do maior para o menor score. */
    List<Movie> recommend(TasteProfile profile, int limit);

    /** Filmes bem avaliados fora dos gêneros do perfil. */
    List<Movie> outsideBubble(TasteProfile profile, int limit);

    /** Filme do dia: escolha determinística por usuário e data entre as melhores recomendações. */
    Optional<Movie> dailyPick(TasteProfile profile, LocalDate date);
}
