package com.filmae.service.collection;

import com.filmae.dto.collection.CollectionSummaryResponse;
import com.filmae.repository.UserMovieRatingRepository;
import com.filmae.repository.UserMovieStateRepository;
import com.filmae.repository.projection.CollectionCountsView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Contagens da coleção calculadas a partir dos dados reais (sempre coerentes entre si). */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CollectionSummaryService {

    private final UserMovieStateRepository userMovieStateRepository;
    private final UserMovieRatingRepository userMovieRatingRepository;

    public CollectionSummaryResponse summary(Long userId) {
        CollectionCountsView counts = userMovieStateRepository.countCollection(userId);
        long rated = userMovieRatingRepository.countByUserId(userId);
        if (counts == null) {
            return new CollectionSummaryResponse(0, 0, 0, 0, 0, rated);
        }
        return CollectionSummaryResponse.builder()
            .total(value(counts.getTotal()))
            .watched(value(counts.getWatched()))
            .watchlist(value(counts.getWatchlist()))
            .favorite(value(counts.getFavorite()))
            .rewatch(value(counts.getRewatch()))
            .rated(rated)
            .build();
    }

    private static long value(Long count) {
        return count != null ? count : 0L;
    }
}
