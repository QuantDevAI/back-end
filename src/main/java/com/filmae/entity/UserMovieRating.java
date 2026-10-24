package com.filmae.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** Nota do usuário (0.5 a 5.0, passo 0.5 — CHECK no banco). Independe da nota do IMDb. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_movie_ratings")
@IdClass(UserMovieId.class)
public class UserMovieRating {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Column(name = "tconst", length = 20)
    private String tconst;

    @Column(name = "rating", nullable = false, precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "rated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant ratedAt;
}
