package com.filmae.service.recommendation;

import com.filmae.entity.Movie;
import com.filmae.entity.MovieRating;
import com.filmae.entity.UserMovieRating;
import com.filmae.entity.UserMovieState;
import com.filmae.entity.UserOnboarding;
import com.filmae.repository.MovieRatingRepository;
import com.filmae.repository.MovieRepository;
import com.filmae.repository.UserMovieRatingRepository;
import com.filmae.repository.UserMovieStateRepository;
import com.filmae.repository.UserOnboardingFavoriteMovieRepository;
import com.filmae.repository.UserOnboardingRepository;
import com.filmae.service.movie.GenreNameResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("HeuristicRecommendationService")
class HeuristicRecommendationServiceTest {

    private static final long USER_ID = 7L;

    @Mock private MovieRepository movieRepository;
    @Mock private MovieRatingRepository movieRatingRepository;
    @Mock private UserOnboardingRepository userOnboardingRepository;
    @Mock private UserOnboardingFavoriteMovieRepository onboardingFavoriteRepository;
    @Mock private UserMovieStateRepository userMovieStateRepository;
    @Mock private UserMovieRatingRepository userMovieRatingRepository;
    @Mock private GenreNameResolver genreNameResolver;

    @InjectMocks
    private HeuristicRecommendationService service;

    private final Map<String, MovieRating> ratings = new HashMap<>();

    @BeforeEach
    void setUp() {
        when(genreNameResolver.namePt(any())).thenAnswer(inv -> switch ((String) inv.getArgument(0)) {
            case "Sci-Fi" -> "Ficção Científica";
            case "Comedy" -> "Comédia";
            default -> inv.getArgument(0);
        });
        when(movieRatingRepository.findAllById(anyIterable())).thenAnswer(inv -> {
            List<MovieRating> found = new ArrayList<>();
            for (Object id : (Iterable<?>) inv.getArgument(0)) {
                MovieRating rating = ratings.get((String) id);
                if (rating != null) {
                    found.add(rating);
                }
            }
            return found;
        });
    }

    private static Movie movie(String tconst, String... genres) {
        return Movie.builder().tconst(tconst).titleType("movie").primaryTitle(tconst).originalTitle(tconst)
            .startYear(2000).runtimeMinutes(120).genres(new ArrayList<>(List.of(genres))).build();
    }

    private MovieRating rating(String tconst, String average, int votes) {
        MovieRating rating = MovieRating.builder().tconst(tconst).averageRating(new BigDecimal(average)).numVotes(votes).build();
        ratings.put(tconst, rating);
        return rating;
    }

    private static TasteProfile onboardingProfile(String... genres) {
        return new TasteProfile(USER_ID, Set.of(genres), Map.of());
    }

    @Nested
    @DisplayName("score")
    class Score {

        @Test
        @DisplayName("Sem perfil, o score é só a nota IMDb ponderada por votos (0 a 100)")
        void emptyProfileUsesOnlyImdb() {
            Movie interstellar = movie("tt1", "Adventure", "Drama", "Sci-Fi");
            MovieRating imdb = rating("tt1", "8.7", 2_200_000);

            int score = service.score(TasteProfile.empty(USER_ID), interstellar, imdb);

            assertThat(score).isEqualTo((int) Math.round(HeuristicRecommendationService.imdbQuality(imdb) * 100));
            assertThat(score).isEqualTo(87);
        }

        @Test
        @DisplayName("Poucos votos puxam a nota para a média (média bayesiana)")
        void fewVotesShrinkTowardsPrior() {
            MovieRating popular = rating("tt1", "8.5", 1_000_000);
            MovieRating obscure = rating("tt2", "8.5", 500);

            assertThat(HeuristicRecommendationService.weightedRating(popular))
                .isGreaterThan(HeuristicRecommendationService.weightedRating(obscure));
            assertThat(HeuristicRecommendationService.weightedRating(obscure)).isCloseTo(6.54, offset(0.05));
            assertThat(HeuristicRecommendationService.weightedRating(null)).isZero();
        }

        @Test
        @DisplayName("Gêneros do onboarding aumentam o score")
        void onboardingGenresRaiseScore() {
            TasteProfile profile = onboardingProfile("Drama", "Sci-Fi");
            Movie match = movie("tt1", "Drama", "Sci-Fi");
            Movie partial = movie("tt2", "Drama", "Comedy");
            Movie none = movie("tt3", "Comedy");
            MovieRating same = rating("tt0", "7.5", 100_000);

            int full = service.score(profile, match, same);
            int half = service.score(profile, partial, same);
            int zero = service.score(profile, none, same);

            assertThat(full).isGreaterThan(half);
            assertThat(half).isGreaterThan(zero);
            double q = HeuristicRecommendationService.imdbQuality(same);
            assertThat(full).isEqualTo((int) Math.round((0.70 + 0.30 * q) * 100));
            assertThat(zero).isEqualTo((int) Math.round(0.30 * q * 100));
        }

        @Test
        @DisplayName("Com onboarding e histórico, os três sinais entram com pesos 0.45/0.30/0.25")
        void combinesAllSignals() {
            TasteProfile profile = new TasteProfile(USER_ID, Set.of("Drama"), Map.of("Drama", 1.0, "Crime", 0.5));
            Movie movie = movie("tt1", "Crime", "Drama");
            MovieRating imdb = rating("tt1", "8.0", 500_000);

            int score = service.score(profile, movie, imdb);

            double expected = 0.45 * 0.5 + 0.30 * 0.75 + 0.25 * HeuristicRecommendationService.imdbQuality(imdb);
            assertThat(score).isEqualTo((int) Math.round(expected * 100));
        }

        @Test
        @DisplayName("Fica entre 0 e 100, inclusive sem nota IMDb e sem gêneros")
        void isBounded() {
            TasteProfile profile = new TasteProfile(USER_ID, Set.of("Drama"), Map.of("Drama", 1.0));
            assertThat(service.score(profile, movie("tt1"), null)).isZero();
            assertThat(service.score(profile, movie("tt2", "Drama"), rating("tt2", "10.0", 10_000_000)))
                .isBetween(0, 100);
        }
    }

    @Nested
    @DisplayName("profileFor")
    class ProfileFor {

        @Test
        @DisplayName("Combina onboarding, favoritos e notas altas e normaliza pelo maior peso")
        void buildsNormalizedHistory() {
            when(userOnboardingRepository.findById(USER_ID)).thenReturn(Optional.of(
                UserOnboarding.builder().userId(USER_ID).genres(new ArrayList<>(List.of("Sci-Fi"))).build()));
            when(userMovieStateRepository.findByUserIdAndFavoriteTrueOrderByUpdatedAtDesc(USER_ID))
                .thenReturn(List.of(UserMovieState.builder().userId(USER_ID).tconst("tt1").favorite(true).build()));
            when(onboardingFavoriteRepository.findByUserIdOrderByPositionAsc(USER_ID)).thenReturn(List.of());
            when(userMovieRatingRepository.findByUserIdAndRatingGreaterThanEqual(eq(USER_ID), any()))
                .thenReturn(List.of(UserMovieRating.builder().userId(USER_ID).tconst("tt2").rating(new BigDecimal("4.0")).build()));
            when(movieRepository.findAllById(anyIterable()))
                .thenReturn(List.of(movie("tt1", "Drama"), movie("tt2", "Comedy")));

            TasteProfile profile = service.profileFor(USER_ID);

            assertThat(profile.onboardingGenres()).containsExactly("Sci-Fi");
            assertThat(profile.historyGenreWeights()).containsEntry("Drama", 1.0).containsEntry("Comedy", 0.5);
            assertThat(profile.coreGenres()).containsExactlyInAnyOrder("Sci-Fi", "Drama", "Comedy");
        }

        @Test
        @DisplayName("Usuário sem dados tem perfil vazio")
        void emptyUser() {
            when(userOnboardingRepository.findById(USER_ID)).thenReturn(Optional.empty());

            TasteProfile profile = service.profileFor(USER_ID);

            assertThat(profile.isEmpty()).isTrue();
        }
    }

    @Nested
    @DisplayName("recommend")
    class Recommend {

        @Test
        @DisplayName("Busca candidatos não assistidos nos gêneros do perfil e ordena por score")
        void ordersByScore() {
            Movie drama = movie("tt1", "Drama");
            Movie comedy = movie("tt2", "Comedy");
            rating("tt1", "7.0", 50_000);
            rating("tt2", "9.0", 2_000_000);
            when(movieRepository.findRecommendationCandidates(eq(USER_ID), eq("Drama"), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(comedy, drama));
            when(movieRepository.findRecommendationCandidates(eq(USER_ID), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(List.of(comedy, drama));

            List<Movie> result = service.recommend(onboardingProfile("Drama"), 2);

            assertThat(result).extracting(Movie::getTconst).containsExactly("tt1", "tt2");
        }
    }

    @Nested
    @DisplayName("outsideBubble")
    class OutsideBubble {

        @Test
        @DisplayName("Exclui os gêneros do perfil e traz os mais bem avaliados (nota >= 7)")
        void excludesProfileGenresAndKeepsWellRated() {
            Movie greatDoc = movie("tt1", "Documentary");
            Movie goodWestern = movie("tt2", "Western");
            Movie weakHorror = movie("tt3", "Horror");
            rating("tt1", "8.8", 300_000);
            rating("tt2", "7.9", 400_000);
            rating("tt3", "5.1", 300_000);
            when(movieRepository.findRecommendationCandidates(eq(USER_ID), isNull(), eq("Drama"), anyInt(), anyInt()))
                .thenReturn(List.of(weakHorror, goodWestern, greatDoc));

            List<Movie> result = service.outsideBubble(onboardingProfile("Drama"), 2);

            assertThat(result).extracting(Movie::getTconst).containsExactly("tt1", "tt2");
            verify(movieRepository).findRecommendationCandidates(eq(USER_ID), isNull(), eq("Drama"), anyInt(), anyInt());
        }

        @Test
        @DisplayName("Se não houver bem avaliados suficientes, completa com os melhores disponíveis")
        void fallsBackWhenNotEnoughWellRated() {
            rating("tt3", "5.1", 300_000);
            when(movieRepository.findRecommendationCandidates(eq(USER_ID), isNull(), eq("Drama"), anyInt(), anyInt()))
                .thenReturn(List.of(movie("tt3", "Horror")));

            assertThat(service.outsideBubble(onboardingProfile("Drama"), 2)).extracting(Movie::getTconst).containsExactly("tt3");
        }
    }

    @Nested
    @DisplayName("dailyPick")
    class DailyPick {

        @BeforeEach
        void candidates() {
            List<Movie> pool = IntStream.rangeClosed(1, 10).mapToObj(i -> {
                rating("tt" + i, "8.0", 100_000 + i);
                return movie("tt" + i, "Drama");
            }).toList();
            when(movieRepository.findRecommendationCandidates(anyLong(), any(), any(), anyInt(), anyInt())).thenReturn(pool);
        }

        @Test
        @DisplayName("Mesmo usuário e mesma data → mesmo filme")
        void deterministicForSameUserAndDate() {
            LocalDate date = LocalDate.of(2026, 10, 24);
            TasteProfile profile = onboardingProfile("Drama");

            Optional<Movie> first = service.dailyPick(profile, date);
            Optional<Movie> second = service.dailyPick(profile, date);

            assertThat(first).isPresent();
            assertThat(second.map(Movie::getTconst)).isEqualTo(first.map(Movie::getTconst));
        }

        @Test
        @DisplayName("Varia ao longo dos dias e entre usuários")
        void variesAcrossDaysAndUsers() {
            Set<Integer> byDay = new HashSet<>();
            Set<Integer> byUser = new HashSet<>();
            LocalDate start = LocalDate.of(2026, 1, 1);
            for (int i = 0; i < 30; i++) {
                byDay.add(HeuristicRecommendationService.dailyIndex(USER_ID, start.plusDays(i), 10));
                byUser.add(HeuristicRecommendationService.dailyIndex((long) i, start, 10));
            }
            assertThat(byDay).hasSizeGreaterThan(3).allMatch(index -> index >= 0 && index < 10);
            assertThat(byUser).hasSizeGreaterThan(3).allMatch(index -> index >= 0 && index < 10);
        }

        @Test
        @DisplayName("Índice é estável (não depende de estado nem de aleatoriedade)")
        void stableIndex() {
            LocalDate date = LocalDate.of(2026, 10, 24);
            int index = HeuristicRecommendationService.dailyIndex(42L, date, 7);
            for (int i = 0; i < 5; i++) {
                assertThat(HeuristicRecommendationService.dailyIndex(42L, date, 7)).isEqualTo(index);
            }
        }

        @Test
        @DisplayName("Sem candidatos → vazio")
        void emptyPool() {
            when(movieRepository.findRecommendationCandidates(anyLong(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
            assertThat(service.dailyPick(onboardingProfile("Drama"), LocalDate.of(2026, 10, 24))).isEmpty();
        }
    }

    @Nested
    @DisplayName("reasons")
    class Reasons {

        @Test
        @DisplayName("Monta frases por template com gêneros em português e nota IMDb")
        void templates() {
            TasteProfile profile = new TasteProfile(USER_ID, Set.of("Drama", "Sci-Fi"), Map.of("Comedy", 1.0));
            Movie movie = movie("tt1", "Drama", "Sci-Fi", "Comedy");

            List<String> reasons = service.reasons(profile, movie, rating("tt1", "8.7", 2_200_000));

            assertThat(reasons).containsExactly(
                "Drama e Ficção Científica estão entre os gêneros que você escolheu.",
                "Você costuma favoritar e avaliar bem filmes de Comédia.",
                "Aclamado no IMDb: nota 8,7 com 2.200.000 votos.");
        }

        @Test
        @DisplayName("Sem nenhum sinal, usa a frase padrão")
        void fallback() {
            assertThat(service.reasons(TasteProfile.empty(USER_ID), movie("tt1", "Horror"), rating("tt1", "5.0", 1000)))
                .containsExactly(HeuristicRecommendationService.FALLBACK_REASON);
        }
    }
}
