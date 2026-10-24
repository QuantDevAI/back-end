package com.filmae.dto.movie;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** {@code MovieDetails} do front: todos os campos de {@link MovieListItemResponse} mais os de detalhe. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovieDetailsResponse {

    @JsonUnwrapped
    private MovieListItemResponse item;

    private String originalTitle;
    /** {@code movie} ou {@code series}. */
    private String type;
    private List<String> cast;
    private String synopsis;
    private List<String> compatibilityReasons;
}
