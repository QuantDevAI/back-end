package com.filmae.dto.collection;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code CollectionSummary} do front. {@code total} = filmes com pelo menos um estado ativo;
 * {@code rated} = filmes com nota do usuário (estejam ou não na coleção).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CollectionSummaryResponse {

    private long total;
    private long watched;
    private long watchlist;
    private long favorite;
    private long rewatch;
    private long rated;
}
