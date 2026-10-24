package com.filmae.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** PUT parcial da coleção: campos {@code null} (omitidos) ficam como estão. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpsertCollectionMovieRequest {

    private Boolean watchlist;
    private Boolean watched;
    private Boolean favorite;
    private Boolean rewatch;
}
