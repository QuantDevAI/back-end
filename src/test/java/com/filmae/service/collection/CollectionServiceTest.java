package com.filmae.service.collection;

import com.filmae.dto.collection.CollectionMovieResponse;
import com.filmae.dto.collection.CollectionResponse;
import com.filmae.dto.collection.CollectionSummaryResponse;
import com.filmae.dto.collection.UpsertCollectionMovieRequest;
import com.filmae.dto.movie.MovieListItemResponse;
import com.filmae.entity.Movie;
import com.filmae.entity.UserMovieId;
import com.filmae.entity.UserMovieState;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import com.filmae.repository.MovieRepository;
import com.filmae.repository.UserMovieRatingRepository;
import com.filmae.repository.UserMovieStateRepository;
import com.filmae.service.movie.MovieAssembler;
import com.filmae.service.movie.MovieCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CollectionService")
class CollectionServiceTest {

    private static final long USER_ID = 3L;
    private static final String TCONST = "tt0816692";
    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-24T08:00:00Z");

    @Mock private UserMovieStateRepository userMovieStateRepository;
    @Mock private UserMovieRatingRepository userMovieRatingRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private MovieCatalogService movieCatalogService;
    @Mock private MovieAssembler movieAssembler;
    @Mock private CollectionSummaryService collectionSummaryService;

    @InjectMocks
    private CollectionService service;

    private final Movie movie = Movie.builder().tconst(TCONST).primaryTitle("Interstellar").originalTitle("Interstellar")
        .titleType("movie").build();
    private final CollectionSummaryResponse summary = new CollectionSummaryResponse(1, 1, 0, 1, 0, 1);

    @BeforeEach
    void setUp() {
        when(movieCatalogService.requireMovie(TCONST)).thenReturn(movie);
        when(movieAssembler.toListItem(USER_ID, movie)).thenReturn(MovieListItemResponse.builder().id(TCONST).build());
        when(collectionSummaryService.summary(USER_ID)).thenReturn(summary);
        when(userMovieStateRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static UserMovieState state(boolean watchlist, boolean watched, boolean favorite, boolean rewatch) {
        return UserMovieState.builder().userId(USER_ID).tconst(TCONST)
            .watchlist(watchlist).watched(watched).favorite(favorite).rewatch(rewatch)
            .watchedAt(watched ? T0 : null).updatedAt(T0).build();
    }

    @Nested
    @DisplayName("PUT parcial")
    class Upsert {

        @Test
        @DisplayName("Campos omitidos ficam como estavam")
        void keepsOmittedFields() {
            UserMovieState current = state(true, false, false, true);

            CollectionService.applyPartial(current, UpsertCollectionMovieRequest.builder().favorite(true).build(), T1);

            assertThat(current.isWatchlist()).isTrue();
            assertThat(current.isWatched()).isFalse();
            assertThat(current.isFavorite()).isTrue();
            assertThat(current.isRewatch()).isTrue();
            assertThat(current.getWatchedAt()).isNull();
        }

        @Test
        @DisplayName("Marcar watched registra watchedAt; remarcar mantém a data; desmarcar limpa")
        void watchedAt() {
            UserMovieState current = state(false, false, false, false);
            UpsertCollectionMovieRequest watched = UpsertCollectionMovieRequest.builder().watched(true).build();

            CollectionService.applyPartial(current, watched, T0);
            assertThat(current.isWatched()).isTrue();
            assertThat(current.getWatchedAt()).isEqualTo(T0);

            CollectionService.applyPartial(current, watched, T1);
            assertThat(current.getWatchedAt()).isEqualTo(T0);

            CollectionService.applyPartial(current, UpsertCollectionMovieRequest.builder().watched(false).build(), T1);
            assertThat(current.isWatched()).isFalse();
            assertThat(current.getWatchedAt()).isNull();
        }

        @Test
        @DisplayName("Body vazio ou nulo não muda nada")
        void emptyBody() {
            UserMovieState current = state(true, true, true, true);

            CollectionService.applyPartial(current, null, T1);
            CollectionService.applyPartial(current, new UpsertCollectionMovieRequest(), T1);

            assertThat(current.isInCollection()).isTrue();
            assertThat(current.getWatchedAt()).isEqualTo(T0);
        }

        @Test
        @DisplayName("Atualiza o registro existente e devolve movie, summary e updatedAt")
        void updatesExisting() {
            when(userMovieStateRepository.findById(UserMovieId.of(USER_ID, TCONST)))
                .thenReturn(Optional.of(state(true, false, false, false)));

            CollectionMovieResponse response = service.upsert(USER_ID, TCONST,
                UpsertCollectionMovieRequest.builder().watched(true).favorite(true).build());

            ArgumentCaptor<UserMovieState> saved = ArgumentCaptor.forClass(UserMovieState.class);
            verify(userMovieStateRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().isWatchlist()).isTrue();
            assertThat(saved.getValue().isWatched()).isTrue();
            assertThat(saved.getValue().isFavorite()).isTrue();
            assertThat(saved.getValue().isRewatch()).isFalse();
            assertThat(saved.getValue().getWatchedAt()).isNotNull();
            assertThat(response.getMovie().getId()).isEqualTo(TCONST);
            assertThat(response.getSummary()).isSameAs(summary);
            assertThat(response.getUpdatedAt()).isEqualTo(T0);
        }

        @Test
        @DisplayName("Cria o registro quando o filme ainda não está na coleção")
        void createsNew() {
            when(userMovieStateRepository.findById(any())).thenReturn(Optional.empty());

            CollectionMovieResponse response = service.upsert(USER_ID, TCONST,
                UpsertCollectionMovieRequest.builder().watchlist(true).build());

            ArgumentCaptor<UserMovieState> saved = ArgumentCaptor.forClass(UserMovieState.class);
            verify(userMovieStateRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getValue().getTconst()).isEqualTo(TCONST);
            assertThat(saved.getValue().isWatchlist()).isTrue();
            assertThat(saved.getValue().isWatched()).isFalse();
            assertThat(response.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Filme inexistente → 404 sem gravar nada")
        void movieNotFound() {
            when(movieCatalogService.requireMovie("tt404")).thenThrow(ApiException.notFound("Filme não encontrado"));

            assertThatThrownBy(() -> service.upsert(USER_ID, "tt404", new UpsertCollectionMovieRequest()))
                .isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
            verify(userMovieStateRepository, never()).saveAndFlush(any());
        }
    }

    @Nested
    @DisplayName("DELETE")
    class Remove {

        @Test
        @DisplayName("Zera os quatro estados e não toca na nota do usuário")
        void clearsFlagsAndKeepsRating() {
            when(userMovieStateRepository.findById(UserMovieId.of(USER_ID, TCONST)))
                .thenReturn(Optional.of(state(true, true, true, true)));

            service.remove(USER_ID, TCONST);

            ArgumentCaptor<UserMovieState> saved = ArgumentCaptor.forClass(UserMovieState.class);
            verify(userMovieStateRepository).saveAndFlush(saved.capture());
            assertThat(saved.getValue().isInCollection()).isFalse();
            assertThat(saved.getValue().getWatchedAt()).isNull();
            verify(userMovieStateRepository, never()).delete(any());
            verifyNoInteractions(userMovieRatingRepository);
        }

        @Test
        @DisplayName("Filme fora da coleção: responde sem gravar")
        void notInCollection() {
            when(userMovieStateRepository.findById(any())).thenReturn(Optional.empty());

            CollectionMovieResponse response = service.remove(USER_ID, TCONST);

            verify(userMovieStateRepository, never()).saveAndFlush(any());
            assertThat(response.getUpdatedAt()).isNotNull();
            assertThat(response.getSummary()).isSameAs(summary);
        }
    }

    @Nested
    @DisplayName("GET")
    class Get {

        @Test
        @DisplayName("Filtro escolhe a consulta certa e mantém a ordem do repositório")
        void filterAndOrder() {
            Movie a = Movie.builder().tconst("tt1").build();
            Movie b = Movie.builder().tconst("tt2").build();
            when(userMovieStateRepository.findByUserIdAndWatchlistTrueOrderByUpdatedAtDesc(USER_ID)).thenReturn(List.of(
                UserMovieState.builder().userId(USER_ID).tconst("tt2").watchlist(true).build(),
                UserMovieState.builder().userId(USER_ID).tconst("tt1").watchlist(true).build()));
            when(movieRepository.findAllById(anyIterable())).thenReturn(List.of(a, b));
            when(movieAssembler.toListItems(eq(USER_ID), anyList())).thenReturn(List.of());

            CollectionResponse response = service.getCollection(USER_ID, CollectionFilter.WATCHLIST);

            verify(movieAssembler).toListItems(USER_ID, List.of(b, a));
            assertThat(response.getSummary()).isSameAs(summary);
        }
    }

    @Test
    @DisplayName("filter: vazio → all, valores do front aceitos, desconhecido → VALIDATION_ERROR")
    void parseFilter() {
        assertThat(CollectionFilter.parse(null)).isEqualTo(CollectionFilter.ALL);
        assertThat(CollectionFilter.parse("")).isEqualTo(CollectionFilter.ALL);
        assertThat(CollectionFilter.parse("rewatch")).isEqualTo(CollectionFilter.REWATCH);
        assertThat(CollectionFilter.parse("Watched")).isEqualTo(CollectionFilter.WATCHED);
        assertThatThrownBy(() -> CollectionFilter.parse("rated"))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                assertThat(ex.getFields()).extracting("field").containsExactly("filter");
            });
    }
}
