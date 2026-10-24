package com.filmae.dto.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code GenreCatalogItem} do front: {@code id} é o nome IMDb e {@code name} o nome em português. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GenreResponse {

    private String id;
    private String name;
    private long movieCount;
}
