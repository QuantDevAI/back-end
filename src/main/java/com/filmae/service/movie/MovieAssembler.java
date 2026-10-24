package com.filmae.service.movie;

import com.filmae.dto.movie.ImdbRatingResponse;
import com.filmae.dto.movie.MovieDetailsResponse;
import com.filmae.dto.movie.MovieListItemResponse;
import com.filmae.dto.movie.MovieUserStateResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Monta {@code MovieListItem} e {@code MovieDetails}. Tudo que depende de outras tabelas (nota IMDb,
 * diretor, título BR, estado e nota do usuário) é carregado em lote para a lista inteira.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MovieAssembler {

    static final int MAX_DIRECTORS = 2;
    static final int MAX_CAST = 10;
    static final String TYPE_MOVIE = "movie";
    static final String TYPE_SERIES = "series";
    static final Set<String> SERIES_TITLE_TYPES = Set.of("tvSeries", "tvMiniSeries");

    private final MovieRatingRepository movieRatingRepository;
    private final MovieCrewRepository movieCrewRepository;
    private final MoviePrincipalRepository moviePrincipalRepository;
    private final PersonRepository personRepository;
    private final AlternativeTitleRepository alternativeTitleRepository;
    private final UserMovieStateRepository userMovieStateRepository;
    private final UserMovieRatingRepository userMovieRatingRepository;
    private final RecommendationService recommendationService;

    public List<MovieListItemResponse> toListItems(Long userId, List<Movie> movies) {
        return toListItems(userId, movies, null);
    }

    /** {@code profile} pode ser passado quando já foi carregado (ex.: GET /home); senão é carregado aqui. */
    public List<MovieListItemResponse> toListItems(Long userId, List<Movie> movies, TasteProfile profile) {
        if (movies.isEmpty()) {
            return List.of();
        }
        Context context = loadContext(userId, movies, profile);
        return movies.stream().map(movie -> toListItem(movie, context)).toList();
    }

    public MovieListItemResponse toListItem(Long userId, Movie movie) {
        return toListItems(userId, List.of(movie)).get(0);
    }

    public MovieDetailsResponse toDetails(Long userId, Movie movie) {
        Context context = loadContext(userId, List.of(movie), null);
        MovieRating rating = context.ratings().get(movie.getTconst());
        return MovieDetailsResponse.builder()
            .item(toListItem(movie, context))
            .originalTitle(movie.getOriginalTitle())
            .type(typeOf(movie.getTitleType()))
            .cast(loadCast(movie.getTconst()))
            .synopsis("")
            .compatibilityReasons(recommendationService.reasons(context.profile(), movie, rating))
            .build();
    }

    /** {@code tvSeries}/{@code tvMiniSeries} → {@code series}; o resto → {@code movie}. */
    public static String typeOf(String titleType) {
        return titleType != null && SERIES_TITLE_TYPES.contains(titleType) ? TYPE_SERIES : TYPE_MOVIE;
    }

    private MovieListItemResponse toListItem(Movie movie, Context context) {
        String tconst = movie.getTconst();
        MovieRating rating = context.ratings().get(tconst);
        MoviePalette palette = MoviePalette.of(movie.getGenres(), tconst);
        return MovieListItemResponse.builder()
            .id(tconst)
            .title(context.brTitles().getOrDefault(tconst, movie.getPrimaryTitle()))
            .year(movie.getStartYear() != null ? movie.getStartYear() : 0)
            .runtimeMinutes(movie.getRuntimeMinutes() != null ? movie.getRuntimeMinutes() : 0)
            .genres(List.copyOf(movie.getGenres()))
            .director(context.directors().getOrDefault(tconst, ""))
            .imdb(rating != null
                ? new ImdbRatingResponse(rating.getAverageRating().doubleValue(), rating.getNumVotes())
                : new ImdbRatingResponse(0, 0))
            .compatibilityScore(recommendationService.score(context.profile(), movie, rating))
            .accentColor(palette.accentColor())
            .gradientColors(palette.gradientColors())
            .userState(userState(context.states().get(tconst), context.userRatings().get(tconst)))
            .build();
    }

    static MovieUserStateResponse userState(UserMovieState state, UserMovieRating rating) {
        Double value = rating != null ? rating.getRating().doubleValue() : null;
        if (state == null) {
            MovieUserStateResponse empty = MovieUserStateResponse.empty();
            empty.setRating(value);
            return empty;
        }
        return new MovieUserStateResponse(state.isWatchlist(), state.isWatched(), state.isFavorite(), state.isRewatch(), value);
    }

    private Context loadContext(Long userId, List<Movie> movies, TasteProfile profile) {
        Set<String> tconsts = movies.stream().map(Movie::getTconst).collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, MovieRating> ratings = byKey(movieRatingRepository.findAllById(tconsts), MovieRating::getTconst);
        Map<String, UserMovieState> states = userId != null
            ? byKey(userMovieStateRepository.findByUserIdAndTconstIn(userId, tconsts), UserMovieState::getTconst)
            : Map.of();
        Map<String, UserMovieRating> userRatings = userId != null
            ? byKey(userMovieRatingRepository.findByUserIdAndTconstIn(userId, tconsts), UserMovieRating::getTconst)
            : Map.of();
        TasteProfile resolvedProfile = profile != null ? profile
            : userId != null ? recommendationService.profileFor(userId) : TasteProfile.empty(null);

        return new Context(ratings, loadDirectors(tconsts), loadBrTitles(tconsts), states, userRatings, resolvedProfile);
    }

    /** Diretores de {@code equipe_filme}; sem equipe, cai para {@code principais_filme} com categoria director. */
    private Map<String, String> loadDirectors(Set<String> tconsts) {
        Map<String, List<String>> nconstsByMovie = new LinkedHashMap<>();
        for (MovieCrew crew : movieCrewRepository.findAllById(tconsts)) {
            List<String> directors = crew.getDirectors().stream().limit(MAX_DIRECTORS).toList();
            if (!directors.isEmpty()) {
                nconstsByMovie.put(crew.getTconst(), directors);
            }
        }
        List<String> missing = tconsts.stream().filter(t -> !nconstsByMovie.containsKey(t)).toList();
        if (!missing.isEmpty()) {
            moviePrincipalRepository
                .findByTconstInAndCategoryInOrderByTconstAscOrderingAsc(missing, List.of("director"))
                .forEach(principal -> {
                    List<String> list = nconstsByMovie.computeIfAbsent(principal.getTconst(), k -> new ArrayList<>());
                    if (list.size() < MAX_DIRECTORS) {
                        list.add(principal.getNconst());
                    }
                });
        }
        if (nconstsByMovie.isEmpty()) {
            return Map.of();
        }

        Set<String> allNconsts = nconstsByMovie.values().stream().flatMap(List::stream).collect(Collectors.toSet());
        Map<String, String> names = loadPersonNames(allNconsts);
        Map<String, String> directors = new HashMap<>();
        nconstsByMovie.forEach((tconst, nconsts) -> {
            String joined = nconsts.stream().map(names::get).filter(name -> name != null && !name.isBlank())
                .collect(Collectors.joining(", "));
            if (!joined.isEmpty()) {
                directors.put(tconst, joined);
            }
        });
        return directors;
    }

    /** Primeiro título da região BR (menor ordering), preferindo o marcado como {@code imdbDisplay}. */
    private Map<String, String> loadBrTitles(Set<String> tconsts) {
        Map<String, String> titles = new HashMap<>();
        Set<String> display = new HashSet<>();
        for (AlternativeTitle aka : alternativeTitleRepository
            .findByTitleIdInAndRegionOrderByTitleIdAscOrderingAsc(tconsts, AlternativeTitle.REGION_BR)) {
            if (aka.getTitle() == null || aka.getTitle().isBlank() || display.contains(aka.getTitleId())) {
                continue;
            }
            boolean isDisplay = aka.getTypes() != null && aka.getTypes().contains("imdbDisplay");
            if (isDisplay) {
                display.add(aka.getTitleId());
                titles.put(aka.getTitleId(), aka.getTitle());
            } else {
                titles.putIfAbsent(aka.getTitleId(), aka.getTitle());
            }
        }
        return titles;
    }

    private List<String> loadCast(String tconst) {
        List<String> nconsts = moviePrincipalRepository.findByTconstOrderByOrderingAsc(tconst).stream()
            .filter(MoviePrincipal::isCast)
            .map(MoviePrincipal::getNconst)
            .distinct()
            .limit(MAX_CAST)
            .toList();
        if (nconsts.isEmpty()) {
            return List.of();
        }
        Map<String, String> names = loadPersonNames(nconsts);
        return nconsts.stream().map(names::get).filter(name -> name != null && !name.isBlank()).toList();
    }

    private Map<String, String> loadPersonNames(Collection<String> nconsts) {
        return personRepository.findAllById(nconsts).stream()
            .collect(Collectors.toMap(Person::getNconst, Person::getPrimaryName, (a, b) -> a));
    }

    private static <T> Map<String, T> byKey(Collection<T> values, Function<T, String> key) {
        return values.stream().collect(Collectors.toMap(key, Function.identity(), (a, b) -> a));
    }

    private record Context(Map<String, MovieRating> ratings,
                           Map<String, String> directors,
                           Map<String, String> brTitles,
                           Map<String, UserMovieState> states,
                           Map<String, UserMovieRating> userRatings,
                           TasteProfile profile) {
    }
}
