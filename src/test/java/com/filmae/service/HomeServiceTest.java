package com.filmae.service;

import com.filmae.dto.collection.CollectionSummaryResponse;
import com.filmae.dto.home.HomeResponse;
import com.filmae.dto.movie.MovieListItemResponse;
import com.filmae.entity.Movie;
import com.filmae.entity.User;
import com.filmae.repository.MovieRatingRepository;
import com.filmae.repository.NotificationRepository;
import com.filmae.service.collection.CollectionSummaryService;
import com.filmae.service.movie.MovieAssembler;
import com.filmae.service.recommendation.RecommendationService;
import com.filmae.service.recommendation.TasteProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HomeService")
class HomeServiceTest {

    private static final long USER_ID = 11L;
    /** 02:00 UTC de 25/10 ainda é 24/10 em Brasília. */
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-25T02:00:00Z"), ZoneOffset.UTC);

    @Mock private RecommendationService recommendationService;
    @Mock private MovieAssembler movieAssembler;
    @Mock private MovieRatingRepository movieRatingRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private CollectionSummaryService collectionSummaryService;
    @Mock private ObjectProvider<Clock> clockProvider;

    private HomeService service;
    private final TasteProfile profile = TasteProfile.empty(USER_ID);
    private final User user = User.builder().id(USER_ID).name("Ana").build();

    @BeforeEach
    void setUp() {
        service = new HomeService(recommendationService, movieAssembler, movieRatingRepository, notificationRepository,
            collectionSummaryService, clockProvider);
        when(clockProvider.getIfAvailable(any())).thenReturn(CLOCK);
        when(recommendationService.profileFor(USER_ID)).thenReturn(profile);
        when(recommendationService.reasons(any(), any(), any())).thenReturn(List.of("Primeiro motivo", "Segundo"));
        when(collectionSummaryService.summary(USER_ID)).thenReturn(new CollectionSummaryResponse(6, 3, 2, 1, 1, 4));
        when(notificationRepository.countByUserIdAndReadFalse(USER_ID)).thenReturn(2L);
        when(movieAssembler.toListItems(eq(USER_ID), anyList(), eq(profile))).thenAnswer(inv -> {
            List<Movie> movies = inv.getArgument(1);
            return movies.stream().map(m -> MovieListItemResponse.builder().id(m.getTconst()).title("T-" + m.getTconst()).build()).toList();
        });
    }

    private static Movie movie(String tconst) {
        return Movie.builder().tconst(tconst).build();
    }

    @Test
    @DisplayName("Monta o payload sem repetir filmes entre filme do dia, recomendações e fora da bolha")
    void buildsPayloadWithoutDuplicates() {
        when(recommendationService.dailyPick(eq(profile), any())).thenReturn(Optional.of(movie("tt2")));
        when(recommendationService.recommend(eq(profile), anyInt())).thenReturn(List.of(movie("tt1"), movie("tt2"), movie("tt3")));
        when(recommendationService.outsideBubble(eq(profile), anyInt())).thenReturn(List.of(movie("tt3"), movie("tt9")));

        HomeResponse home = service.home(user);

        assertThat(home.getUser().getId()).isEqualTo(USER_ID);
        assertThat(home.getUser().getName()).isEqualTo("Ana");
        assertThat(home.getUser().isOnboardingCompleted()).isFalse();
        assertThat(home.getCollectionSummary()).isEqualTo(new HomeResponse.HomeCollectionSummary(2, 3, 1, 1));
        assertThat(home.getDailyMovie().getMovie().getId()).isEqualTo("tt2");
        assertThat(home.getDailyMovie().getReason()).isEqualTo("Primeiro motivo");
        assertThat(home.getRecommendations()).extracting(MovieListItemResponse::getId).containsExactly("tt1", "tt3");
        assertThat(home.getOutsideBubble()).extracting(MovieListItemResponse::getId).containsExactly("tt9");
        assertThat(home.getUnreadNotifications()).isEqualTo(2);
    }

    @Test
    @DisplayName("Cria a notificação daily_recommendation com dedupeKey do dia (horário de Brasília)")
    void createsDailyNotification() {
        when(recommendationService.dailyPick(eq(profile), eq(LocalDate.of(2026, 10, 24)))).thenReturn(Optional.of(movie("tt2")));
        when(recommendationService.recommend(eq(profile), anyInt())).thenReturn(List.of());
        when(recommendationService.outsideBubble(eq(profile), anyInt())).thenReturn(List.of());

        service.home(user);

        verify(notificationRepository).insertIfAbsent(USER_ID, "daily_recommendation", HomeService.DAILY_NOTIFICATION_TITLE,
            "T-tt2 combina com a sessão de hoje.", "daily_recommendation:2026-10-24");
    }

    @Test
    @DisplayName("Sem filme do dia: dailyMovie null e nenhuma notificação")
    void noDailyMovie() {
        when(recommendationService.dailyPick(eq(profile), any())).thenReturn(Optional.empty());
        when(recommendationService.recommend(eq(profile), anyInt())).thenReturn(List.of());
        when(recommendationService.outsideBubble(eq(profile), anyInt())).thenReturn(List.of());

        HomeResponse home = service.home(user);

        assertThat(home.getDailyMovie()).isNull();
        assertThat(home.getRecommendations()).isEmpty();
        verify(notificationRepository, never()).insertIfAbsent(any(), any(), any(), any(), any());
    }
}
