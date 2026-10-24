package com.filmae.dto.collection;

import com.filmae.dto.movie.MovieListItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * {@code CollectionMoviePayload} do front, também usado como resposta de PUT/DELETE de nota
 * ({@code SaveMovieRatingResponse}/{@code DeleteMovieRatingResponse}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionMovieResponse {

    private MovieListItemResponse movie;
    private CollectionSummaryResponse summary;
    private Instant updatedAt;
}
