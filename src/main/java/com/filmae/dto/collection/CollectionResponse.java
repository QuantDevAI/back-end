package com.filmae.dto.collection;

import com.filmae.dto.movie.MovieListItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** {@code CollectionPayload} do front. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionResponse {

    private List<MovieListItemResponse> items;
    private CollectionSummaryResponse summary;
}
