package com.filmae.service.collection;

import com.filmae.dto.collection.CollectionMovieResponse;
import com.filmae.dto.collection.CollectionSummaryResponse;
import com.filmae.dto.movie.MovieListItemResponse;
import com.filmae.entity.Movie;
import com.filmae.entity.UserMovieId;
import com.filmae.entity.UserMovieRating;
import com.filmae.exception.ApiException;
import com.filmae.exception.ErrorCode;
import com.filmae.repository.UserMovieRatingRepository;
import com.filmae.service.movie.MovieAssembler;
import com.filmae.service.movie.MovieCatalogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RatingService")
class RatingServiceTest {

    private static final long USER_ID = 5L;
    private static final String TCONST = "tt0816692";

    @Mock private UserMovieRatingRepository userMovieRatingRepository;
    @Mock private MovieCatalogService movieCatalogService;
    @Mock private MovieAssembler movieAssembler;
    @Mock private CollectionSummaryService collectionSummaryService;

    @InjectMocks
    private RatingService service;

    private final Movie movie = Movie.builder().tconst(TCONST).build();

    @BeforeEach
    void setUp() {
        when(movieCatalogService.requireMovie(TCONST)).thenReturn(movie);
        when(movieAssembler.toListItem(USER_ID, movie)).thenReturn(MovieListItemResponse.builder().id(TCONST).build());
        when(collectionSummaryService.summary(USER_ID)).thenReturn(new CollectionSummaryResponse());
        when(userMovieRatingRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @ParameterizedTest(name = "{0} é válida")
    @ValueSource(strings = {"0.5", "1", "2.5", "3.0", "4.50", "5"})
    @DisplayName("Aceita 0.5 a 5 em passos de 0.5")
    void acceptsValidRatings(String value) {
        BigDecimal result = RatingService.validate(new BigDecimal(value));

        assertThat(result.scale()).isEqualTo(1);
        assertThat(result).isEqualByComparingTo(value);
    }

    @ParameterizedTest(name = "{0} é inválida")
    @ValueSource(strings = {"0", "0.4", "-1", "5.5", "6", "3.3", "4.25", "4.75"})
    @DisplayName("Rejeita fora da escala ou fora do passo com VALIDATION_ERROR no campo rating")
    void rejectsInvalidRatings(String value) {
        assertThatThrownBy(() -> RatingService.validate(new BigDecimal(value)))
            .isInstanceOfSatisfying(ApiException.class, ex -> {
                assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                assertThat(ex.getFields()).extracting("field").containsExactly("rating");
            });
    }

    @Test
    @DisplayName("Nota nula é inválida")
    void rejectsNull() {
        assertThatThrownBy(() -> RatingService.validate(null)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Nota inválida não consulta nem grava nada")
    void invalidDoesNotTouchRepository() {
        assertThatThrownBy(() -> service.save(USER_ID, TCONST, new BigDecimal("7")))
            .isInstanceOf(ApiException.class);
        verifyNoInteractions(userMovieRatingRepository, movieCatalogService);
    }

    @Test
    @DisplayName("PUT grava a nota e devolve movie, summary e updatedAt = ratedAt")
    void saves() {
        when(userMovieRatingRepository.findById(UserMovieId.of(USER_ID, TCONST))).thenReturn(Optional.empty());

        CollectionMovieResponse response = service.save(USER_ID, TCONST, new BigDecimal("4.5"));

        ArgumentCaptor<UserMovieRating> saved = ArgumentCaptor.forClass(UserMovieRating.class);
        verify(userMovieRatingRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getRating()).isEqualByComparingTo("4.5");
        assertThat(saved.getValue().getRatedAt()).isNotNull();
        assertThat(response.getUpdatedAt()).isEqualTo(saved.getValue().getRatedAt());
        assertThat(response.getMovie().getId()).isEqualTo(TCONST);
        assertThat(response.getSummary()).isNotNull();
    }

    @Test
    @DisplayName("GET devolve a nota ou null")
    void get() {
        when(userMovieRatingRepository.findById(UserMovieId.of(USER_ID, TCONST))).thenReturn(Optional.of(
            UserMovieRating.builder().userId(USER_ID).tconst(TCONST).rating(new BigDecimal("3.5")).ratedAt(Instant.now()).build()));
        assertThat(service.get(USER_ID, TCONST).getRating()).isEqualTo(3.5);

        when(userMovieRatingRepository.findById(UserMovieId.of(USER_ID, TCONST))).thenReturn(Optional.empty());
        assertThat(service.get(USER_ID, TCONST).getRating()).isNull();
    }

    @Test
    @DisplayName("DELETE remove só se existir e devolve o payload completo")
    void delete() {
        UserMovieRating existing = UserMovieRating.builder().userId(USER_ID).tconst(TCONST).rating(BigDecimal.ONE).build();
        when(userMovieRatingRepository.findById(UserMovieId.of(USER_ID, TCONST))).thenReturn(Optional.of(existing));

        CollectionMovieResponse response = service.delete(USER_ID, TCONST);

        verify(userMovieRatingRepository).delete(existing);
        assertThat(response.getUpdatedAt()).isNotNull();

        when(userMovieRatingRepository.findById(UserMovieId.of(USER_ID, TCONST))).thenReturn(Optional.empty());
        service.delete(USER_ID, TCONST);
        verify(userMovieRatingRepository, never()).deleteById(any());
    }
}
