package com.filmae.repository;

import com.filmae.entity.Movie;
import com.filmae.repository.projection.GenreCountView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Filtros de lista de gêneros são passados como CSV de nomes IMDb (ex.: {@code "Drama,Sci-Fi"}) e
 * convertidos com {@code string_to_array} no SQL; {@code null} desliga o filtro.
 */
@Repository
public interface MovieRepository extends JpaRepository<Movie, String> {

    String SEARCH_FROM_WHERE = """
        FROM filmes f
        LEFT JOIN avaliacoes_filmes a ON a.tconst = f.tconst
        WHERE f.is_adult = FALSE
          AND (CAST(:titleType AS text) IS NULL OR f.title_type = CAST(:titleType AS text))
          AND (CAST(:q AS text) IS NULL
               OR f.primary_title ILIKE '%' || CAST(:q AS text) || '%'
               OR f.original_title ILIKE '%' || CAST(:q AS text) || '%'
               OR EXISTS (SELECT 1 FROM titulos_alternativos t
                          WHERE t.title_id = f.tconst AND t.title ILIKE '%' || CAST(:q AS text) || '%'))
          AND (CAST(:genresCsv AS text) IS NULL OR f.genres && string_to_array(CAST(:genresCsv AS text), ','))
          AND (CAST(:yearFrom AS integer) IS NULL OR f.start_year >= CAST(:yearFrom AS integer))
          AND (CAST(:yearTo AS integer) IS NULL OR f.start_year <= CAST(:yearTo AS integer))
          AND (CAST(:minRating AS numeric) IS NULL OR a.average_rating >= CAST(:minRating AS numeric))
        """;

    /**
     * Busca paginada ordenada por popularidade (votos IMDb). {@code q} deve vir já escapado para ILIKE
     * ({@code %}, {@code _}, {@code \}); {@code genresCsv} casa se o filme tiver QUALQUER um dos gêneros.
     * Passe um {@link Pageable} sem {@code Sort} (a ordenação é fixa).
     */
    @Query(
        value = "SELECT f.* " + SEARCH_FROM_WHERE + " ORDER BY a.num_votes DESC NULLS LAST, f.tconst",
        countQuery = "SELECT count(*) " + SEARCH_FROM_WHERE,
        nativeQuery = true
    )
    Page<Movie> search(
        @Param("q") String q,
        @Param("genresCsv") String genresCsv,
        @Param("titleType") String titleType,
        @Param("yearFrom") Integer yearFrom,
        @Param("yearTo") Integer yearTo,
        @Param("minRating") BigDecimal minRating,
        Pageable pageable
    );

    /**
     * Candidatos para recomendação: filmes com nota IMDb e pelo menos {@code minVotes} votos, que o
     * usuário ainda não assistiu, opcionalmente com algum gênero de {@code includeGenresCsv} e
     * nenhum de {@code excludeGenresCsv}. Ordenados por votos e nota.
     */
    @Query(value = """
        SELECT f.* FROM filmes f
        JOIN avaliacoes_filmes a ON a.tconst = f.tconst
        WHERE f.is_adult = FALSE
          AND a.num_votes >= :minVotes
          AND (CAST(:includeGenresCsv AS text) IS NULL OR f.genres && string_to_array(CAST(:includeGenresCsv AS text), ','))
          AND (CAST(:excludeGenresCsv AS text) IS NULL OR NOT (f.genres && string_to_array(CAST(:excludeGenresCsv AS text), ',')))
          AND NOT EXISTS (SELECT 1 FROM user_movie_states s
                          WHERE s.user_id = :userId AND s.tconst = f.tconst AND s.watched)
        ORDER BY a.num_votes DESC, a.average_rating DESC, f.tconst
        LIMIT :limit
        """, nativeQuery = true)
    List<Movie> findRecommendationCandidates(
        @Param("userId") Long userId,
        @Param("includeGenresCsv") String includeGenresCsv,
        @Param("excludeGenresCsv") String excludeGenresCsv,
        @Param("minVotes") int minVotes,
        @Param("limit") int limit
    );

    /** Quantidade de filmes (não adultos) por gênero IMDb, para GET /genres. */
    @Query(value = """
        SELECT g AS "genre", count(*) AS "movieCount"
        FROM filmes f CROSS JOIN LATERAL unnest(f.genres) AS g
        WHERE f.is_adult = FALSE
        GROUP BY g
        ORDER BY count(*) DESC, g
        """, nativeQuery = true)
    List<GenreCountView> countMoviesByGenre();
}
