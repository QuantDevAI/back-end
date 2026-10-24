package com.filmae.repository;

import com.filmae.entity.MovieRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Nota IMDb ({@code avaliacoes_filmes}); use {@code findAllById} para carregar em lote. */
@Repository
public interface MovieRatingRepository extends JpaRepository<MovieRating, String> {
}
