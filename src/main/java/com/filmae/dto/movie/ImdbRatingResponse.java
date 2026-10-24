package com.filmae.dto.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code ImdbRating} do front. Sem nota no IMDb: {@code {rating: 0, votes: 0}}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImdbRatingResponse {

    private double rating;
    private long votes;
}
