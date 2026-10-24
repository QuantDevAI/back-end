package com.filmae.repository;

import com.filmae.entity.UserMovieId;
import com.filmae.entity.UserMovieRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface UserMovieRatingRepository extends JpaRepository<UserMovieRating, UserMovieId> {

    List<UserMovieRating> findByUserIdAndTconstIn(Long userId, Collection<String> tconsts);

    List<UserMovieRating> findByUserIdOrderByRatedAtDesc(Long userId);

    /** Notas altas do usuário (sinal da recomendação), ex.: {@code minRating = 4.0}. */
    List<UserMovieRating> findByUserIdAndRatingGreaterThanEqual(Long userId, BigDecimal minRating);

    long countByUserId(Long userId);
}
