package com.filmae.service;

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
import com.filmae.repository.projection.GenreCountView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService")
class DashboardServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private UserMovieStateRepository userMovieStateRepository;
    @Mock private UserMovieRatingRepository userMovieRatingRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private AlternativeTitleRepository alternativeTitleRepository;

    @InjectMocks private DashboardService service;

    @Test
    @DisplayName("KPIs usam as contagens da coleção, a soma de runtime e o total de notas")
    void kpisComeFromCountsRuntimeAndRatings() {
        when(userMovieStateRepository.countCollection(USER_ID)).thenReturn(counts(15L, 4L, 9L, 3L, 2L));
        when(userMovieStateRepository.sumWatchedRuntimeMinutes(USER_ID)).thenReturn(5160L);
        when(userMovieRatingRepository.countByUserId(USER_ID)).thenReturn(21L);
        when(userMovieStateRepository.findTopWatchedGenres(USER_ID, DashboardService.TOP_GENRES_LIMIT)).thenReturn(List.of());
        when(userMovieStateRepository.findWatchHistory(eq(USER_ID), any(Pageable.class))).thenReturn(List.of());

        DashboardResponse response = service.getDashboard(USER_ID);

        assertThat(response.kpis().watchedCount()).isEqualTo(9);
        assertThat(response.kpis().totalMinutesWatched()).isEqualTo(5160);
        assertThat(response.kpis().favoriteCount()).isEqualTo(3);
        assertThat(response.kpis().rewatchCount()).isEqualTo(2);
        assertThat(response.kpis().ratedCount()).isEqualTo(21);
        assertThat(response.kpis().watchlistCount()).isEqualTo(4);
        assertThat(response.historyPreview()).isEmpty();
        assertThat(response.updatedAt()).isNotNull();
        verify(movieRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("Sem coleção, os KPIs saem zerados (inclusive totalMinutesWatched)")
    void emptyCollectionGivesZeroKpis() {
        when(userMovieStateRepository.countCollection(USER_ID)).thenReturn(counts(null, null, null, null, null));
        when(userMovieStateRepository.sumWatchedRuntimeMinutes(USER_ID)).thenReturn(0L);
        when(userMovieStateRepository.findTopWatchedGenres(USER_ID, DashboardService.TOP_GENRES_LIMIT)).thenReturn(List.of());
        when(userMovieStateRepository.findWatchHistory(eq(USER_ID), any(Pageable.class))).thenReturn(List.of());

        DashboardResponse response = service.getDashboard(USER_ID);

        assertThat(response.kpis().watchedCount()).isZero();
        assertThat(response.kpis().totalMinutesWatched()).isZero();
        assertThat(response.kpis().watchlistCount()).isZero();
        assertThat(response.topGenres()).isEmpty();
    }

    @Test
    @DisplayName("topGenres mantém a ordem da query e mapeia genre/count")
    void topGenresKeepQueryOrder() {
        stubEmptyKpis();
        when(userMovieStateRepository.findTopWatchedGenres(USER_ID, DashboardService.TOP_GENRES_LIMIT))
            .thenReturn(List.of(genre("Drama", 18L), genre("Sci-Fi", 12L), genre("Adventure", 3L)));
        when(userMovieStateRepository.findWatchHistory(eq(USER_ID), any(Pageable.class))).thenReturn(List.of());

        DashboardResponse response = service.getDashboard(USER_ID);

        assertThat(response.topGenres())
            .extracting(g -> g.genre() + ":" + g.count())
            .containsExactly("Drama:18", "Sci-Fi:12", "Adventure:3");
    }

    @Test
    @DisplayName("historyPreview segue a ordem por watchedAt desc, usa título BR e rating null quando não há nota")
    void historyPreviewKeepsOrderAndUsesBrazilianTitle() {
        stubEmptyKpis();
        when(userMovieStateRepository.findTopWatchedGenres(USER_ID, DashboardService.TOP_GENRES_LIMIT)).thenReturn(List.of());
        Instant newest = Instant.parse("2026-10-02T16:00:00Z");
        Instant older = Instant.parse("2026-09-15T10:00:00Z");
        when(userMovieStateRepository.findWatchHistory(eq(USER_ID), any(Pageable.class)))
            .thenReturn(List.of(watched("tt0816692", newest), watched("tt0133093", older)));
        when(movieRepository.findAllById(anyCollection())).thenReturn(List.of(
            movie("tt0133093", "The Matrix", 1999),
            movie("tt0816692", "Interstellar", 2014)));
        when(userMovieRatingRepository.findByUserIdAndTconstIn(eq(USER_ID), anyCollection())).thenReturn(List.of(
            UserMovieRating.builder().userId(USER_ID).tconst("tt0816692").rating(new BigDecimal("4.5")).ratedAt(newest).build()));
        when(alternativeTitleRepository.findByTitleIdInAndRegionOrderByTitleIdAscOrderingAsc(anyCollection(), eq("BR")))
            .thenReturn(List.of(
                AlternativeTitle.builder().titleId("tt0816692").ordering(1).title("Interestelar").region("BR").build(),
                AlternativeTitle.builder().titleId("tt0816692").ordering(2).title("Outro título").region("BR").build()));

        List<WatchHistoryEntry> history = service.getDashboard(USER_ID).historyPreview();

        assertThat(history).containsExactly(
            new WatchHistoryEntry("tt0816692", "Interestelar", 2014, newest, new BigDecimal("4.5")),
            new WatchHistoryEntry("tt0133093", "The Matrix", 1999, older, null));
    }

    private void stubEmptyKpis() {
        when(userMovieStateRepository.countCollection(USER_ID)).thenReturn(counts(0L, 0L, 0L, 0L, 0L));
        when(userMovieStateRepository.sumWatchedRuntimeMinutes(USER_ID)).thenReturn(0L);
    }

    private static UserMovieState watched(String tconst, Instant watchedAt) {
        return UserMovieState.builder().userId(USER_ID).tconst(tconst).watched(true).watchedAt(watchedAt).build();
    }

    private static Movie movie(String tconst, String title, int year) {
        return Movie.builder().tconst(tconst).titleType("movie").primaryTitle(title).originalTitle(title)
            .startYear(year).runtimeMinutes(120).build();
    }

    private static GenreCountView genre(String name, Long count) {
        return new GenreCountView() {
            @Override public String getGenre() { return name; }
            @Override public Long getMovieCount() { return count; }
        };
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
