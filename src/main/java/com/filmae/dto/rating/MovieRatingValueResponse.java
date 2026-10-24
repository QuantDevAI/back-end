package com.filmae.dto.rating;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** GET /users/me/ratings/{tconst}: {@code {rating: number | null}}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieRatingValueResponse {

    private Double rating;
}
