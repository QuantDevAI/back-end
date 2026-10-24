package com.filmae.dto.home;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.filmae.dto.movie.MovieListItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** {@code HomePayload} do front. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HomeResponse {

    private HomeUser user;
    private HomeCollectionSummary collectionSummary;
    private HomeDailyMovie dailyMovie;
    private List<MovieListItemResponse> recommendations;
    private List<MovieListItemResponse> outsideBubble;
    private long unreadNotifications;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HomeUser {
        private Long id;
        private String name;
        private boolean onboardingCompleted;
    }

    /** Só as quatro contagens (sem {@code total} e {@code rated}). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HomeCollectionSummary {
        private long watchlist;
        private long watched;
        private long favorite;
        private long rewatch;
    }

    /** {@code MovieListItem & {reason}}. */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HomeDailyMovie {
        @JsonUnwrapped
        private MovieListItemResponse movie;
        private String reason;
    }
}
