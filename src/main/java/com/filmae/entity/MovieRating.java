package com.filmae.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Nota global do IMDb (tabela {@code avaliacoes_filmes}). Não confundir com {@link UserMovieRating}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "avaliacoes_filmes")
public class MovieRating {

    @Id
    @Column(name = "tconst", length = 20)
    private String tconst;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 1)
    private BigDecimal averageRating;

    @Column(name = "num_votes", nullable = false)
    private Integer numVotes;
}
