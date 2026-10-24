package com.filmae.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Chave composta (user_id, tconst) de {@link UserMovieState}, {@link UserMovieRating} e {@link UserOnboardingFavoriteMovie}. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserMovieId implements Serializable {

    private Long userId;
    private String tconst;

    public static UserMovieId of(Long userId, String tconst) {
        return new UserMovieId(userId, tconst);
    }
}
