package com.filmae.service.collection;

import com.filmae.dto.collection.CollectionSummaryResponse;
import com.filmae.repository.UserMovieRatingRepository;
import com.filmae.repository.UserMovieStateRepository;
import com.filmae.repository.projection.CollectionCountsView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CollectionSummaryService")
class CollectionSummaryServiceTest {

    @Mock private UserMovieStateRepository userMovieStateRepository;
    @Mock private UserMovieRatingRepository userMovieRatingRepository;

    @InjectMocks
    private CollectionSummaryService service;

    @Test
    @DisplayName("Usa as contagens reais do banco e a quantidade de notas")
    void mapsCounts() {
        when(userMovieStateRepository.countCollection(1L)).thenReturn(counts(5L, 2L, 3L, 1L, 1L));
        when(userMovieRatingRepository.countByUserId(1L)).thenReturn(4L);

        CollectionSummaryResponse summary = service.summary(1L);

        assertThat(summary).isEqualTo(new CollectionSummaryResponse(5, 3, 2, 1, 1, 4));
        assertThat(summary.getTotal()).isGreaterThanOrEqualTo(Math.max(
            Math.max(summary.getWatched(), summary.getWatchlist()), Math.max(summary.getFavorite(), summary.getRewatch())));
    }

    @Test
    @DisplayName("Sem linhas ou contagens nulas vira zero")
    void handlesNulls() {
        when(userMovieStateRepository.countCollection(1L)).thenReturn(counts(null, null, null, null, null));
        when(userMovieRatingRepository.countByUserId(1L)).thenReturn(0L);
        assertThat(service.summary(1L)).isEqualTo(new CollectionSummaryResponse(0, 0, 0, 0, 0, 0));

        when(userMovieStateRepository.countCollection(2L)).thenReturn(null);
        when(userMovieRatingRepository.countByUserId(2L)).thenReturn(2L);
        assertThat(service.summary(2L)).isEqualTo(new CollectionSummaryResponse(0, 0, 0, 0, 0, 2));
    }

    private static CollectionCountsView counts(Long total, Long watchlist, Long watched, Long favorite, Long rewatch) {
        return new CollectionCountsView() {
            @Override public Long getTotal() { return total; }
            @Override public Long getWatchlist() { return watchlist; }
            @Override public Long getWatched() { return watched; }
            @Override public Long getFavorite() { return favorite; }
            @Override public Long getRewatch() { return rewatch; }
        };
    }
}
