package com.filmae.service.collection;

import com.filmae.dto.collection.CollectionMovieResponse;
import com.filmae.dto.collection.CollectionResponse;
import com.filmae.dto.collection.UpsertCollectionMovieRequest;
import com.filmae.entity.Movie;
import com.filmae.entity.UserMovieId;
import com.filmae.entity.UserMovieRating;
import com.filmae.entity.UserMovieState;
import com.filmae.repository.MovieRepository;
import com.filmae.repository.UserMovieRatingRepository;
import com.filmae.repository.UserMovieStateRepository;
import com.filmae.service.movie.MovieAssembler;
import com.filmae.service.movie.MovieCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Coleção do usuário: quatro estados independentes por filme. O PUT é parcial e o DELETE zera os
 * estados mantendo a nota do usuário (gerenciada em /users/me/ratings).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CollectionService {

    private final UserMovieStateRepository userMovieStateRepository;
    private final UserMovieRatingRepository userMovieRatingRepository;
    private final MovieRepository movieRepository;
    private final MovieCatalogService movieCatalogService;
    private final MovieAssembler movieAssembler;
    private final CollectionSummaryService collectionSummaryService;

    @Transactional(readOnly = true)
    public CollectionResponse getCollection(Long userId, CollectionFilter filter) {
        List<UserMovieState> states = switch (filter) {
            case ALL -> userMovieStateRepository.findCollection(userId);
            case WATCHLIST -> userMovieStateRepository.findByUserIdAndWatchlistTrueOrderByUpdatedAtDesc(userId);
            case WATCHED -> userMovieStateRepository.findByUserIdAndWatchedTrueOrderByUpdatedAtDesc(userId);
            case FAVORITE -> userMovieStateRepository.findByUserIdAndFavoriteTrueOrderByUpdatedAtDesc(userId);
            case REWATCH -> userMovieStateRepository.findByUserIdAndRewatchTrueOrderByUpdatedAtDesc(userId);
        };

        List<String> tconsts = states.stream().map(UserMovieState::getTconst).toList();
        Map<String, Movie> movies = movieRepository.findAllById(tconsts).stream()
            .collect(Collectors.toMap(Movie::getTconst, Function.identity()));
        List<Movie> ordered = tconsts.stream().map(movies::get).filter(Objects::nonNull).toList();

        return CollectionResponse.builder()
            .items(movieAssembler.toListItems(userId, ordered))
            .summary(collectionSummaryService.summary(userId))
            .build();
    }

    @Transactional(readOnly = true)
    public CollectionMovieResponse getMovie(Long userId, String tconst) {
        Movie movie = movieCatalogService.requireMovie(tconst);
        Optional<UserMovieState> state = userMovieStateRepository.findById(UserMovieId.of(userId, tconst));
        Optional<UserMovieRating> rating = userMovieRatingRepository.findById(UserMovieId.of(userId, tconst));
        Instant updatedAt = Stream.of(state.map(UserMovieState::getUpdatedAt), rating.map(UserMovieRating::getRatedAt))
            .flatMap(Optional::stream)
            .max(Instant::compareTo)
            .orElseGet(CollectionService::now);
        return response(userId, movie, updatedAt);
    }

    public CollectionMovieResponse upsert(Long userId, String tconst, UpsertCollectionMovieRequest request) {
        Movie movie = movieCatalogService.requireMovie(tconst);
        UserMovieState state = userMovieStateRepository.findById(UserMovieId.of(userId, tconst))
            .orElseGet(() -> UserMovieState.emptyFor(userId, tconst));
        applyPartial(state, request, now());
        UserMovieState saved = userMovieStateRepository.saveAndFlush(state);
        return response(userId, movie, saved.getUpdatedAt() != null ? saved.getUpdatedAt() : now());
    }

    public CollectionMovieResponse remove(Long userId, String tconst) {
        Movie movie = movieCatalogService.requireMovie(tconst);
        Instant updatedAt = userMovieStateRepository.findById(UserMovieId.of(userId, tconst))
            .map(state -> {
                clear(state);
                UserMovieState saved = userMovieStateRepository.saveAndFlush(state);
                return saved.getUpdatedAt();
            })
            .orElse(null);
        return response(userId, movie, updatedAt != null ? updatedAt : now());
    }

    /**
     * Aplica só os campos informados. Ao virar {@code watched = true} registra {@code watchedAt};
     * ao desmarcar, limpa.
     */
    static void applyPartial(UserMovieState state, UpsertCollectionMovieRequest request, Instant now) {
        if (request == null) {
            return;
        }
        if (request.getWatchlist() != null) {
            state.setWatchlist(request.getWatchlist());
        }
        if (request.getFavorite() != null) {
            state.setFavorite(request.getFavorite());
        }
        if (request.getRewatch() != null) {
            state.setRewatch(request.getRewatch());
        }
        if (request.getWatched() != null) {
            boolean watched = request.getWatched();
            if (watched && (!state.isWatched() || state.getWatchedAt() == null)) {
                state.setWatchedAt(now);
            } else if (!watched) {
                state.setWatchedAt(null);
            }
            state.setWatched(watched);
        }
    }

    /** Zera os quatro estados; a nota do usuário fica em outra tabela e não é tocada. */
    static void clear(UserMovieState state) {
        state.clearFlags();
        state.setWatchedAt(null);
    }

    private CollectionMovieResponse response(Long userId, Movie movie, Instant updatedAt) {
        return CollectionMovieResponse.builder()
            .movie(movieAssembler.toListItem(userId, movie))
            .summary(collectionSummaryService.summary(userId))
            .updatedAt(updatedAt)
            .build();
    }

    static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
