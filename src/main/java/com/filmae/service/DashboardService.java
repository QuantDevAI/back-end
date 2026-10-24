package com.filmae.service;

import com.filmae.dto.dashboard.DashboardGenreInsight;
import com.filmae.dto.dashboard.DashboardKpis;
import com.filmae.dto.dashboard.DashboardResponse;
import com.filmae.dto.dashboard.WatchHistoryEntry;
import com.filmae.entity.AlternativeTitle;
import com.filmae.entity.Movie;
import com.filmae.entity.UserMovieRating;
import com.filmae.entity.UserMovieState;
import com.filmae.repository.AlternativeTitleRepository;
import com.filmae.repository.MovieRepository;
import com.filmae.repository.UserMovieRatingRepository;
import com.filmae.repository.UserMovieStateRepository;
import com.filmae.repository.projection.CollectionCountsView;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Monta o {@code GET /users/me/dashboard} a partir da coleção e das notas do usuário. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

    static final int TOP_GENRES_LIMIT = 5;
    static final int HISTORY_PREVIEW_LIMIT = 10;

    private final UserMovieStateRepository userMovieStateRepository;
    private final UserMovieRatingRepository userMovieRatingRepository;
    private final MovieRepository movieRepository;
    private final AlternativeTitleRepository alternativeTitleRepository;

    public DashboardResponse getDashboard(Long userId) {
        return new DashboardResponse(kpis(userId), topGenres(userId), historyPreview(userId), Instant.now());
    }

    private DashboardKpis kpis(Long userId) {
        CollectionCountsView counts = userMovieStateRepository.countCollection(userId);
        return new DashboardKpis(
            count(counts != null ? counts.getWatched() : null),
            userMovieStateRepository.sumWatchedRuntimeMinutes(userId),
            count(counts != null ? counts.getFavorite() : null),
            count(counts != null ? counts.getRewatch() : null),
            userMovieRatingRepository.countByUserId(userId),
            count(counts != null ? counts.getWatchlist() : null)
        );
    }

    private List<DashboardGenreInsight> topGenres(Long userId) {
        return userMovieStateRepository.findTopWatchedGenres(userId, TOP_GENRES_LIMIT).stream()
            .map(view -> new DashboardGenreInsight(view.getGenre(), count(view.getMovieCount())))
            .toList();
    }

    private List<WatchHistoryEntry> historyPreview(Long userId) {
        List<UserMovieState> history =
            userMovieStateRepository.findWatchHistory(userId, PageRequest.of(0, HISTORY_PREVIEW_LIMIT));
        if (history.isEmpty()) {
            return List.of();
        }
        List<String> tconsts = history.stream().map(UserMovieState::getTconst).toList();

        Map<String, Movie> movies = movieRepository.findAllById(tconsts).stream()
            .collect(Collectors.toMap(Movie::getTconst, Function.identity()));
        Map<String, BigDecimal> ratings = userMovieRatingRepository.findByUserIdAndTconstIn(userId, tconsts).stream()
            .collect(Collectors.toMap(UserMovieRating::getTconst, UserMovieRating::getRating));
        Map<String, String> brTitles = alternativeTitleRepository
            .findByTitleIdInAndRegionOrderByTitleIdAscOrderingAsc(tconsts, AlternativeTitle.REGION_BR).stream()
            .collect(Collectors.toMap(AlternativeTitle::getTitleId, AlternativeTitle::getTitle, (first, ignored) -> first));

        return history.stream()
            .map(state -> {
                Movie movie = movies.get(state.getTconst());
                String title = brTitles.getOrDefault(state.getTconst(),
                    movie != null ? movie.getPrimaryTitle() : state.getTconst());
                return new WatchHistoryEntry(
                    state.getTconst(),
                    title,
                    movie != null ? movie.getStartYear() : null,
                    state.getWatchedAt() != null ? state.getWatchedAt() : state.getUpdatedAt(),
                    ratings.get(state.getTconst())
                );
            })
            .toList();
    }

    private static long count(Long value) {
        return value != null ? value : 0L;
    }
}
