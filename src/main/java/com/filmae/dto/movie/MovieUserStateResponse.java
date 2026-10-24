package com.filmae.dto.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code MovieUserState} do front. {@code rating} é a nota do usuário (0.5 a 5) ou {@code null}. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieUserStateResponse {

    private boolean watchlist;
    private boolean watched;
    private boolean favorite;
    private boolean rewatch;
    private Double rating;

    public static MovieUserStateResponse empty() {
        return new MovieUserStateResponse(false, false, false, false, null);
    }
}
