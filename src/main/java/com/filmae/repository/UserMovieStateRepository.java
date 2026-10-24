package com.filmae.repository;

import com.filmae.entity.UserMovieId;
import com.filmae.entity.UserMovieState;
import com.filmae.repository.projection.CollectionCountsView;
import com.filmae.repository.projection.GenreCountView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface UserMovieStateRepository extends JpaRepository<UserMovieState, UserMovieId> {

    /** Carga em lote do estado de vários filmes para um usuário (montagem de {@code userState}). */
    List<UserMovieState> findByUserIdAndTconstIn(Long userId, Collection<String> tconsts);

    /** Filmes com pelo menos uma flag ativa (filtro {@code all}), mais recentes primeiro. */
    @Query("""
        SELECT s FROM UserMovieState s
        WHERE s.userId = :userId AND (s.watchlist = TRUE OR s.watched = TRUE OR s.favorite = TRUE OR s.rewatch = TRUE)
        ORDER BY s.updatedAt DESC
        """)
    List<UserMovieState> findCollection(@Param("userId") Long userId);

    List<UserMovieState> findByUserIdAndWatchlistTrueOrderByUpdatedAtDesc(Long userId);

    List<UserMovieState> findByUserIdAndWatchedTrueOrderByUpdatedAtDesc(Long userId);

    List<UserMovieState> findByUserIdAndFavoriteTrueOrderByUpdatedAtDesc(Long userId);

    List<UserMovieState> findByUserIdAndRewatchTrueOrderByUpdatedAtDesc(Long userId);

    /** Histórico de assistidos por {@code watched_at} decrescente (dashboard). */
    @Query("""
        SELECT s FROM UserMovieState s
        WHERE s.userId = :userId AND s.watched = TRUE
        ORDER BY s.watchedAt DESC NULLS LAST, s.updatedAt DESC
        """)
    List<UserMovieState> findWatchHistory(@Param("userId") Long userId, Pageable pageable);

    @Query(value = """
        SELECT count(*) FILTER (WHERE watchlist OR watched OR favorite OR rewatch) AS "total",
               count(*) FILTER (WHERE watchlist) AS "watchlist",
               count(*) FILTER (WHERE watched)   AS "watched",
               count(*) FILTER (WHERE favorite)  AS "favorite",
               count(*) FILTER (WHERE rewatch)   AS "rewatch"
        FROM user_movie_states
        WHERE user_id = :userId
        """, nativeQuery = true)
    CollectionCountsView countCollection(@Param("userId") Long userId);

    /** Soma de {@code runtime_minutes} dos filmes assistidos. */
    @Query(value = """
        SELECT COALESCE(SUM(f.runtime_minutes), 0)
        FROM user_movie_states s JOIN filmes f ON f.tconst = s.tconst
        WHERE s.user_id = :userId AND s.watched
        """, nativeQuery = true)
    long sumWatchedRuntimeMinutes(@Param("userId") Long userId);

    /** Gêneros IMDb mais frequentes entre os filmes assistidos. */
    @Query(value = """
        SELECT g AS "genre", count(*) AS "movieCount"
        FROM user_movie_states s
        JOIN filmes f ON f.tconst = s.tconst
        CROSS JOIN LATERAL unnest(f.genres) AS g
        WHERE s.user_id = :userId AND s.watched
        GROUP BY g
        ORDER BY count(*) DESC, g
        LIMIT :limit
        """, nativeQuery = true)
    List<GenreCountView> findTopWatchedGenres(@Param("userId") Long userId, @Param("limit") int limit);
}
