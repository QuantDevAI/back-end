package com.filmae.dto.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** {@code MovieListItem} do front (home, busca e coleção). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieListItemResponse {

    private String id;
    private String title;
    private int year;
    private int runtimeMinutes;
    private List<String> genres;
    private String director;
    private ImdbRatingResponse imdb;
    private int compatibilityScore;
    private String accentColor;
    private List<String> gradientColors;
    private MovieUserStateResponse userState;
}
