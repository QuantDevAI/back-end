package com.filmae.service.movie;

import com.filmae.dto.movie.MovieDetailsResponse;
import com.filmae.dto.movie.MovieListItemResponse;
import com.filmae.entity.AlternativeTitle;
import com.filmae.entity.Movie;
import com.filmae.entity.MovieCrew;
import com.filmae.entity.MoviePrincipal;
import com.filmae.entity.MovieRating;
import com.filmae.entity.Person;
import com.filmae.entity.UserMovieRating;
import com.filmae.entity.UserMovieState;
import com.filmae.repository.AlternativeTitleRepository;
import com.filmae.repository.MovieCrewRepository;
import com.filmae.repository.MoviePrincipalRepository;
import com.filmae.repository.MovieRatingRepository;
import com.filmae.repository.PersonRepository;
import com.filmae.repository.UserMovieRatingRepository;
import com.filmae.repository.UserMovieStateRepository;
import com.filmae.service.recommendation.RecommendationService;
import com.filmae.service.recommendation.TasteProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("MovieAssembler")
class MovieAssemblerTest {

    private static final long USER_ID = 9L;

    @Mock private MovieRatingRepository movieRatingRepository;
    @Mock private MovieCrewRepository movieCrewRepository;
    @Mock private MoviePrincipalRepository moviePrincipalRepository;
    @Mock private PersonRepository personRepository;
    @Mock private AlternativeTitleRepository alternativeTitleRepository;
    @Mock private UserMovieStateRepository userMovieStateRepository;
    @Mock private UserMovieRatingRepository userMovieRatingRepository;
    @Mock private RecommendationService recommendationService;

    @InjectMocks
    private MovieAssembler assembler;

    private final Movie interstellar = Movie.builder().tconst("tt1").titleType("movie").primaryTitle("Interstellar")
        .originalTitle("Interstellar").startYear(2014).runtimeMinutes(169)
        .genres(new ArrayList<>(List.of("Adventure", "Drama", "Sci-Fi"))).build();
    private final Movie series = Movie.builder().tconst("tt2").titleType("tvSeries").primaryTitle("Dark")
        .originalTitle("Dark").genres(new ArrayList<>(List.of("Crime"))).build();

    @BeforeEach
    void setUp() {
        when(recommendationService.profileFor(USER_ID)).thenReturn(TasteProfile.empty(USER_ID));
        when(recommendationService.score(any(), any(), any())).thenReturn(94);
        when(recommendationService.reasons(any(), any(), any())).thenReturn(List.of("Motivo"));
        when(movieRatingRepository.findAllById(anyIterable())).thenReturn(List.of(
            MovieRating.builder().tconst("tt1").averageRating(new BigDecimal("8.7")).numVotes(2_200_000).build()));
        when(movieCrewRepository.findAllById(anyIterable())).thenReturn(List.of(
            MovieCrew.builder().tconst("tt1").directors(new ArrayList<>(List.of("nm1"))).build()));
        when(moviePrincipalRepository.findByTconstInAndCategoryInOrderByTconstAscOrderingAsc(anyCollection(), anyCollection()))
            .thenReturn(List.of(MoviePrincipal.builder().tconst("tt2").ordering(1).nconst("nm2").category("director").build()));
        when(personRepository.findAllById(anyIterable())).thenReturn(List.of(
            Person.builder().nconst("nm1").primaryName("Christopher Nolan").build(),
            Person.builder().nconst("nm2").primaryName("Baran bo Odar").build(),
            Person.builder().nconst("nm3").primaryName("Matthew McConaughey").build(),
            Person.builder().nconst("nm4").primaryName("Anne Hathaway").build()));
        when(alternativeTitleRepository.findByTitleIdInAndRegionOrderByTitleIdAscOrderingAsc(anyCollection(), eq("BR")))
            .thenReturn(List.of(
                AlternativeTitle.builder().titleId("tt1").ordering(1).title("Interestelar (versão)").region("BR").build(),
                AlternativeTitle.builder().titleId("tt1").ordering(2).title("Interestelar").region("BR")
                    .types(new ArrayList<>(List.of("imdbDisplay"))).build()));
        when(userMovieStateRepository.findByUserIdAndTconstIn(eq(USER_ID), anyCollection())).thenReturn(List.of(
            UserMovieState.builder().userId(USER_ID).tconst("tt1").watchlist(true).favorite(true).build()));
        when(userMovieRatingRepository.findByUserIdAndTconstIn(eq(USER_ID), anyCollection())).thenReturn(List.of(
            UserMovieRating.builder().userId(USER_ID).tconst("tt1").rating(new BigDecimal("4.5")).build()));
    }

    @Test
    @DisplayName("Lista: título BR, diretor, IMDb, cores, score e userState carregados em lote")
    void listItems() {
        List<MovieListItemResponse> items = assembler.toListItems(USER_ID, List.of(interstellar, series));

        MovieListItemResponse first = items.get(0);
        assertThat(first.getId()).isEqualTo("tt1");
        assertThat(first.getTitle()).isEqualTo("Interestelar");
        assertThat(first.getYear()).isEqualTo(2014);
        assertThat(first.getRuntimeMinutes()).isEqualTo(169);
        assertThat(first.getGenres()).containsExactly("Adventure", "Drama", "Sci-Fi");
        assertThat(first.getDirector()).isEqualTo("Christopher Nolan");
        assertThat(first.getImdb().getRating()).isEqualTo(8.7);
        assertThat(first.getImdb().getVotes()).isEqualTo(2_200_000);
        assertThat(first.getCompatibilityScore()).isEqualTo(94);
        assertThat(first.getAccentColor()).isEqualTo(MoviePalette.of("Adventure", "tt1").accentColor());
        assertThat(first.getGradientColors()).hasSize(4);
        assertThat(first.getUserState().isWatchlist()).isTrue();
        assertThat(first.getUserState().isFavorite()).isTrue();
        assertThat(first.getUserState().isWatched()).isFalse();
        assertThat(first.getUserState().getRating()).isEqualTo(4.5);

        MovieListItemResponse second = items.get(1);
        assertThat(second.getTitle()).isEqualTo("Dark");
        assertThat(second.getDirector()).isEqualTo("Baran bo Odar");
        assertThat(second.getImdb().getRating()).isZero();
        assertThat(second.getYear()).isZero();
        assertThat(second.getUserState().isWatchlist()).isFalse();
        assertThat(second.getUserState().getRating()).isNull();

        verify(movieRatingRepository, times(1)).findAllById(anyIterable());
        verify(userMovieStateRepository, times(1)).findByUserIdAndTconstIn(anyLong(), anyCollection());
        verify(recommendationService, times(1)).profileFor(USER_ID);
    }

    @Test
    @DisplayName("Detalhes: originalTitle, type, cast dos principais, synopsis vazia e reasons")
    void details() {
        when(moviePrincipalRepository.findByTconstOrderByOrderingAsc("tt1")).thenReturn(List.of(
            MoviePrincipal.builder().tconst("tt1").ordering(1).nconst("nm1").category("director").build(),
            MoviePrincipal.builder().tconst("tt1").ordering(2).nconst("nm3").category("actor").build(),
            MoviePrincipal.builder().tconst("tt1").ordering(3).nconst("nm4").category("actress").build()));

        MovieDetailsResponse details = assembler.toDetails(USER_ID, interstellar);

        assertThat(details.getItem().getTitle()).isEqualTo("Interestelar");
        assertThat(details.getOriginalTitle()).isEqualTo("Interstellar");
        assertThat(details.getType()).isEqualTo("movie");
        assertThat(details.getCast()).containsExactly("Matthew McConaughey", "Anne Hathaway");
        assertThat(details.getSynopsis()).isEmpty();
        assertThat(details.getCompatibilityReasons()).containsExactly("Motivo");
    }

    @Test
    @DisplayName("type: tvSeries/tvMiniSeries → series; resto → movie")
    void type() {
        assertThat(MovieAssembler.typeOf("tvSeries")).isEqualTo("series");
        assertThat(MovieAssembler.typeOf("tvMiniSeries")).isEqualTo("series");
        assertThat(MovieAssembler.typeOf("movie")).isEqualTo("movie");
        assertThat(MovieAssembler.typeOf("tvMovie")).isEqualTo("movie");
        assertThat(MovieAssembler.typeOf(null)).isEqualTo("movie");
    }

    @Test
    @DisplayName("Lista vazia não consulta nada")
    void empty() {
        assertThat(assembler.toListItems(USER_ID, List.of())).isEmpty();
        verify(movieRatingRepository, times(0)).findAllById(anyIterable());
    }
}
