package com.filmae.service.recommendation;

import com.filmae.entity.Movie;
import com.filmae.entity.MovieRating;
import com.filmae.entity.UserMovieRating;
import com.filmae.entity.UserMovieState;
import com.filmae.entity.UserOnboardingFavoriteMovie;
import com.filmae.repository.MovieRatingRepository;
import com.filmae.repository.MovieRepository;
import com.filmae.repository.UserMovieRatingRepository;
import com.filmae.repository.UserMovieStateRepository;
import com.filmae.repository.UserOnboardingFavoriteMovieRepository;
import com.filmae.repository.UserOnboardingRepository;
import com.filmae.service.movie.GenreNameResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Recomendação por heurística com três sinais:
 * <ol>
 *   <li>gêneros do filme que batem com os escolhidos no onboarding;</li>
 *   <li>gêneros dos favoritos (coleção e onboarding) e das notas altas do usuário;</li>
 *   <li>nota do IMDb ponderada pelo número de votos (média bayesiana).</li>
 * </ol>
 * Os pesos são redistribuídos quando o usuário não tem onboarding ou histórico.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class HeuristicRecommendationService implements RecommendationService {

    static final double W_ONBOARDING = 0.45;
    static final double W_HISTORY = 0.30;
    static final double W_IMDB = 0.25;
    static final double W_SINGLE_TASTE = 0.70;
    static final double W_SINGLE_IMDB = 0.30;

    /** Média bayesiana do IMDb: {@code m} votos "de prior" com média {@code C}. */
    static final double PRIOR_VOTES = 25_000;
    static final double PRIOR_MEAN = 6.5;

    static final int MIN_VOTES = 1_000;
    static final int MIN_CANDIDATES = 50;
    static final int CANDIDATE_FACTOR = 5;
    static final int DAILY_POOL_SIZE = 10;
    static final double OUTSIDE_MIN_IMDB = 7.0;
    static final BigDecimal HIGH_USER_RATING = new BigDecimal("4.0");

    static final double FAVORITE_WEIGHT = 2.0;
    static final double ONBOARDING_FAVORITE_WEIGHT = 1.5;

    static final String FALLBACK_REASON = "Uma aposta para ampliar seu repertório.";

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final MovieRepository movieRepository;
    private final MovieRatingRepository movieRatingRepository;
    private final UserOnboardingRepository userOnboardingRepository;
    private final UserOnboardingFavoriteMovieRepository onboardingFavoriteRepository;
    private final UserMovieStateRepository userMovieStateRepository;
    private final UserMovieRatingRepository userMovieRatingRepository;
    private final GenreNameResolver genreNameResolver;

    @Override
    public TasteProfile profileFor(Long userId) {
        Set<String> onboardingGenres = userOnboardingRepository.findById(userId)
            .map(onboarding -> (Set<String>) new LinkedHashSet<>(onboarding.getGenres()))
            .orElseGet(Set::of);

        Map<String, Double> movieWeights = new HashMap<>();
        for (UserMovieState state : userMovieStateRepository.findByUserIdAndFavoriteTrueOrderByUpdatedAtDesc(userId)) {
            movieWeights.merge(state.getTconst(), FAVORITE_WEIGHT, Double::sum);
        }
        for (UserOnboardingFavoriteMovie favorite : onboardingFavoriteRepository.findByUserIdOrderByPositionAsc(userId)) {
            movieWeights.merge(favorite.getTconst(), ONBOARDING_FAVORITE_WEIGHT, Double::sum);
        }
        for (UserMovieRating rating : userMovieRatingRepository.findByUserIdAndRatingGreaterThanEqual(userId, HIGH_USER_RATING)) {
            movieWeights.merge(rating.getTconst(), rating.getRating().doubleValue() - 3.0, Double::sum);
        }

        Map<String, Double> genreWeights = new HashMap<>();
        if (!movieWeights.isEmpty()) {
            for (Movie movie : movieRepository.findAllById(movieWeights.keySet())) {
                double weight = movieWeights.get(movie.getTconst());
                movie.getGenres().forEach(genre -> genreWeights.merge(genre, weight, Double::sum));
            }
        }
        return new TasteProfile(userId, onboardingGenres, normalize(genreWeights));
    }

    @Override
    public int score(TasteProfile profile, Movie movie, MovieRating imdbRating) {
        double imdb = imdbQuality(imdbRating);
        List<String> genres = movie.getGenres();
        double onboarding = 0;
        double history = 0;
        if (!genres.isEmpty()) {
            onboarding = genres.stream().filter(profile.onboardingGenres()::contains).count() / (double) genres.size();
            history = genres.stream().mapToDouble(profile::historyWeight).sum() / genres.size();
        }

        double value;
        if (profile.hasOnboarding() && profile.hasHistory()) {
            value = W_ONBOARDING * onboarding + W_HISTORY * history + W_IMDB * imdb;
        } else if (profile.hasOnboarding()) {
            value = W_SINGLE_TASTE * onboarding + W_SINGLE_IMDB * imdb;
        } else if (profile.hasHistory()) {
            value = W_SINGLE_TASTE * history + W_SINGLE_IMDB * imdb;
        } else {
            value = imdb;
        }
        return (int) Math.max(0, Math.min(100, Math.round(value * 100)));
    }

    @Override
    public List<String> reasons(TasteProfile profile, Movie movie, MovieRating imdbRating) {
        List<String> reasons = new ArrayList<>();

        List<String> onboardingMatches = movie.getGenres().stream()
            .filter(profile.onboardingGenres()::contains)
            .map(genreNameResolver::namePt)
            .toList();
        if (!onboardingMatches.isEmpty()) {
            reasons.add(joinPt(onboardingMatches)
                + (onboardingMatches.size() == 1 ? " está" : " estão")
                + " entre os gêneros que você escolheu.");
        }

        movie.getGenres().stream()
            .filter(genre -> profile.historyWeight(genre) >= TasteProfile.CORE_HISTORY_WEIGHT)
            .max(Comparator.comparingDouble(profile::historyWeight))
            .ifPresent(genre -> reasons.add("Você costuma favoritar e avaliar bem filmes de "
                + genreNameResolver.namePt(genre) + "."));

        if (imdbRating != null) {
            double rating = imdbRating.getAverageRating().doubleValue();
            int votes = imdbRating.getNumVotes();
            if (rating >= 8.0 && votes >= 100_000) {
                reasons.add("Aclamado no IMDb: nota " + formatRating(rating) + " com "
                    + NumberFormat.getIntegerInstance(PT_BR).format(votes) + " votos.");
            } else if (rating >= 7.0) {
                reasons.add("Bem avaliado no IMDb (nota " + formatRating(rating) + ").");
            }
        }

        if (reasons.isEmpty()) {
            reasons.add(FALLBACK_REASON);
        }
        return reasons;
    }

    @Override
    public List<Movie> recommend(TasteProfile profile, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int candidateLimit = Math.max(limit * CANDIDATE_FACTOR, MIN_CANDIDATES);
        Set<String> core = profile.coreGenres();

        Map<String, Movie> candidates = new LinkedHashMap<>();
        if (!core.isEmpty()) {
            movieRepository.findRecommendationCandidates(profile.userId(), csv(core), null, MIN_VOTES, candidateLimit)
                .forEach(movie -> candidates.putIfAbsent(movie.getTconst(), movie));
        }
        if (candidates.size() < limit) {
            movieRepository.findRecommendationCandidates(profile.userId(), null, null, MIN_VOTES, candidateLimit)
                .forEach(movie -> candidates.putIfAbsent(movie.getTconst(), movie));
        }

        Map<String, MovieRating> ratings = loadRatings(candidates.keySet());
        Map<String, Integer> scores = candidates.values().stream()
            .collect(Collectors.toMap(Movie::getTconst, movie -> score(profile, movie, ratings.get(movie.getTconst()))));

        return candidates.values().stream()
            .sorted(Comparator.<Movie>comparingInt(movie -> scores.get(movie.getTconst())).reversed()
                .thenComparing(Comparator.<Movie>comparingLong(movie -> votes(ratings.get(movie.getTconst()))).reversed())
                .thenComparing(Movie::getTconst))
            .limit(limit)
            .toList();
    }

    @Override
    public List<Movie> outsideBubble(TasteProfile profile, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        int candidateLimit = Math.max(limit * CANDIDATE_FACTOR, MIN_CANDIDATES);
        Set<String> core = profile.coreGenres();
        List<Movie> candidates = movieRepository.findRecommendationCandidates(
            profile.userId(), null, core.isEmpty() ? null : csv(core), MIN_VOTES, candidateLimit);

        Map<String, MovieRating> ratings = loadRatings(candidates.stream().map(Movie::getTconst).toList());
        Comparator<Movie> byQuality = Comparator.<Movie>comparingDouble(movie -> weightedRating(ratings.get(movie.getTconst())))
            .reversed()
            .thenComparing(Movie::getTconst);

        List<Movie> wellRated = candidates.stream()
            .filter(movie -> weightedRating(ratings.get(movie.getTconst())) >= OUTSIDE_MIN_IMDB)
            .sorted(byQuality)
            .limit(limit)
            .toList();
        if (wellRated.size() >= limit) {
            return wellRated;
        }
        return candidates.stream().sorted(byQuality).limit(limit).toList();
    }

    @Override
    public Optional<Movie> dailyPick(TasteProfile profile, LocalDate date) {
        List<Movie> pool = recommend(profile, DAILY_POOL_SIZE);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(pool.get(dailyIndex(profile.userId(), date, pool.size())));
    }

    /** Índice estável em {@code [0, size)} para o par usuário + data. */
    static int dailyIndex(Long userId, LocalDate date, int size) {
        long seed = (userId != null ? userId : 0L) * 0x9E3779B97F4A7C15L ^ date.toEpochDay();
        return (int) Math.floorMod(mix64(seed), (long) size);
    }

    /** Finalizador do MurmurHash3: espalha bem seeds próximas (usuários e dias consecutivos). */
    static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xFF51AFD7ED558CCDL;
        z = (z ^ (z >>> 33)) * 0xC4CEB9FE1A85EC53L;
        return z ^ (z >>> 33);
    }

    /** Média bayesiana em escala 0–10; filme sem nota vale 0. */
    static double weightedRating(MovieRating rating) {
        if (rating == null) {
            return 0;
        }
        double votes = rating.getNumVotes();
        double average = rating.getAverageRating().doubleValue();
        return (votes / (votes + PRIOR_VOTES)) * average + (PRIOR_VOTES / (votes + PRIOR_VOTES)) * PRIOR_MEAN;
    }

    static double imdbQuality(MovieRating rating) {
        return weightedRating(rating) / 10.0;
    }

    private static long votes(MovieRating rating) {
        return rating != null ? rating.getNumVotes() : 0;
    }

    private Map<String, MovieRating> loadRatings(Collection<String> tconsts) {
        if (tconsts.isEmpty()) {
            return Map.of();
        }
        return movieRatingRepository.findAllById(tconsts).stream()
            .collect(Collectors.toMap(MovieRating::getTconst, Function.identity()));
    }

    private static Map<String, Double> normalize(Map<String, Double> weights) {
        double max = weights.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (max <= 0) {
            return Map.of();
        }
        Map<String, Double> normalized = new HashMap<>();
        weights.forEach((genre, weight) -> normalized.put(genre, weight / max));
        return normalized;
    }

    private static String csv(Collection<String> genres) {
        return String.join(",", genres);
    }

    private static String formatRating(double rating) {
        return String.format(PT_BR, "%.1f", rating);
    }

    private static String joinPt(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + " e " + names.get(names.size() - 1);
    }
}
